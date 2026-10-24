/*
  ===================================================================================
  ResQMesh - Off-Grid Disaster Communication System
  Firmware: Bluetooth BLE <-> SX1278 (Ra-02 433MHz) LoRa Radio Modem Bridge
  Target Hardware: ESP32-WROOM-32 + SX1278 (Ra-02 433 MHz)
  ===================================================================================

  Wiring Diagram (ESP32-WROOM-32 to Ra-02 SX1278):
  --------------------------------------------------
  SX1278 Pin   <--->  ESP32 Pin
  VCC          <--->  3.3V   (NEVER CONNECT TO 5V!)
  GND          <--->  GND
  NSS / CS     <--->  GPIO 5
  RST          <--->  GPIO 14
  DIO0         <--->  GPIO 2
  SCK          <--->  GPIO 18
  MISO         <--->  GPIO 19
  MOSI         <--->  GPIO 23
  ===================================================================================
*/

#include <SPI.h>
#include <LoRa.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

#define SS      5    // SPI Chip Select (NSS)
#define RST     14   // Hardware Reset
#define DIO0    2    // Packet Received Interrupt Pin

#define SERVICE_UUID           "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
#define CHARACTERISTIC_UUID_RX "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"
#define CHARACTERISTIC_UUID_TX "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"

BLECharacteristic *pTxCharacteristic;
bool deviceConnected = false;

// Queue outgoing LoRa payload outside BLE interrupt context for stability
String pendingLoraTx = "";
volatile bool hasPendingLoraTx = false;

class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
      deviceConnected = true;
      Serial.println(">>> ResQMesh Android App connected via Bluetooth BLE! <<<");
    }

    void onDisconnect(BLEServer* pServer) {
      deviceConnected = false;
      Serial.println(">>> ResQMesh Android App disconnected. Restarting BLE Advertising... <<<");
      delay(200);
      BLEDevice::startAdvertising();
    }
};

class MyCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
      String rxValue = pCharacteristic->getValue().c_str();
      if (rxValue.length() > 0) {
        pendingLoraTx = rxValue;
        hasPendingLoraTx = true;
      }
    }
};

void setup() {
  Serial.begin(115200);
  delay(200);

  Serial.println("\n==================================================");
  Serial.println("         ResQMesh LoRa Node Starting Up...         ");
  Serial.println("==================================================");

  // 1. Initialize SX1278 LoRa Module over SPI
  SPI.begin(18, 19, 23, 5);
  LoRa.setPins(SS, RST, DIO0);
  if (!LoRa.begin(433E6)) {
    Serial.println("ERROR: LoRa initialization failed! Check wiring or 3.3V power.");
    while (1) {
      delay(1000);
    }
  }

  LoRa.setTxPower(20);
  LoRa.setSpreadingFactor(10);
  LoRa.setSignalBandwidth(125E3);
  LoRa.enableCrc();

  Serial.println("SX1278 LoRa Module Initialized at 433 MHz (+20 dBm)");

  // 2. Initialize Bluetooth BLE Server with high MTU
  BLEDevice::init("ResQMesh-LoRa-2");
  BLEDevice::setMTU(512);

  BLEServer *pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  BLEService *pService = pServer->createService(SERVICE_UUID);

  // TX Characteristic (ESP32 -> Android Phone via Notify)
  pTxCharacteristic = pService->createCharacteristic(
          CHARACTERISTIC_UUID_TX,
          BLECharacteristic::PROPERTY_NOTIFY
  );
  pTxCharacteristic->addDescriptor(new BLE2902());

  // RX Characteristic (Android Phone -> ESP32 via Write)
  BLECharacteristic *pRxCharacteristic = pService->createCharacteristic(
          CHARACTERISTIC_UUID_RX,
          BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR
  );
  pRxCharacteristic->setCallbacks(new MyCallbacks());

  pService->start();

  // CRITICAL FIX: Attach SERVICE_UUID to Advertising Packet so Android ScanFilter finds it!
  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06);
  pAdvertising->setMinPreferred(0x12);
  BLEDevice::startAdvertising();

  // Put LoRa in continuous receive mode
  LoRa.receive();

  Serial.println("Bluetooth BLE Active & Advertising SERVICE_UUID as 'ResQMesh-LoRa-1'");
  Serial.println("==================================================\n");
}

void loop() {
  // 1. Handle outgoing LoRa transmission queued from BLE write
  if (hasPendingLoraTx) {
    String payload = pendingLoraTx;
    hasPendingLoraTx = false;

    Serial.print("Transmitting over 433MHz LoRa (");
    Serial.print(payload.length());
    Serial.print(" bytes): ");
    Serial.println(payload);

    // SX1278 max single packet size is 255 bytes; cap at 250 bytes per frame
    LoRa.beginPacket();
    LoRa.write((const uint8_t*)payload.c_str(), min((unsigned int)payload.length(), 250U));
    LoRa.endPacket();

    // Return to receive mode immediately after TX
    LoRa.receive();
    Serial.println(">>> LoRa Packet Transmitted Successfully! <<<");
  }

  // 2. Check if an incoming LoRa packet was received over the air
  int packetSize = LoRa.parsePacket();
  if (packetSize > 0) {
    String incoming = "";
    while (LoRa.available()) {
      incoming += (char)LoRa.read();
    }

    Serial.print("LoRa Packet Received (RSSI ");
    Serial.print(LoRa.packetRssi());
    Serial.print(" dBm): ");
    Serial.println(incoming);

    if (deviceConnected && incoming.length() > 0) {
      pTxCharacteristic->setValue((uint8_t*)incoming.c_str(), incoming.length());
      pTxCharacteristic->notify();
      Serial.println(">>> Forwarded LoRa packet to Android App via BLE! <<<");
    } else {
      Serial.println("Phone not connected over BLE. Packet not forwarded.");
    }
  }
}