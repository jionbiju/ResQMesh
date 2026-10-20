/*
  ===================================================================================
  ResQMesh - Off-Grid Disaster Communication System
  Firmware: Bluetooth BLE <-> SX1278 (Ra-02 433MHz) LoRa Radio Modem Bridge
  Target Hardware: ESP32-WROOM-32 + SX1278 (Ra-02 433 MHz)
  ===================================================================================

  Wiring Diagram (ESP32-WROOM-32 to Ra-02 SX1278):
  --------------------------------------------------
  SX1278 Pin   <--->  ESP32 Pin
  VCC          <--->  3.3V   (⚠️ NEVER CONNECT TO 5V!)
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

// ---------------------------------------------------------------------------------
// Pin Definitions for SX1278 (Ra-02) SPI Interface
// ---------------------------------------------------------------------------------
#define SS      5    // SPI Chip Select (NSS)
#define RST     14   // Hardware Reset
#define DIO0    2    // Packet Received Interrupt Pin

// ---------------------------------------------------------------------------------
// Nordic UART BLE UUIDs for ResQMesh Android App Communication
// ---------------------------------------------------------------------------------
#define SERVICE_UUID           "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
#define CHARACTERISTIC_UUID_RX "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"
#define CHARACTERISTIC_UUID_TX "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"

BLECharacteristic *pTxCharacteristic;
bool deviceConnected = false;

// ---------------------------------------------------------------------------------
// Bluetooth BLE Connection Callbacks
// ---------------------------------------------------------------------------------
class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
      deviceConnected = true;
      Serial.println(">>> ResQMesh Android App connected via Bluetooth BLE! <<<");
    }

    void onDisconnect(BLEServer* pServer) {
      deviceConnected = false;
      Serial.println(">>> ResQMesh Android App disconnected. Restarting BLE Advertising... <<<");
      BLEDevice::startAdvertising(); // Allow phone to reconnect seamlessly
    }
};

// ---------------------------------------------------------------------------------
// BLE Write Callback (Handles incoming packet from Phone -> Broadcasts via LoRa)
// ---------------------------------------------------------------------------------
class MyCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
      std::string rxValue = pCharacteristic->getValue();
      if (rxValue.length() > 0) {
        Serial.print("Sending Message over 433MHz LoRa Radio: ");
        Serial.println(rxValue.c_str());

        // Transmit packet over SX1278 LoRa RF
        LoRa.beginPacket();
        LoRa.print(rxValue.c_str());
        LoRa.endPacket();
        Serial.println(">>> LoRa Packet Transmitted Successfully! <<<");
      }
    }
};

// ---------------------------------------------------------------------------------
// Arduino Setup Function
// ---------------------------------------------------------------------------------
void setup() {
  // Initialize Serial Monitor for debugging
  Serial.begin(115200);
  while (!Serial) delay(10);

  Serial.println("\n==================================================");
  Serial.println("         ResQMesh LoRa Node Starting Up...         ");
  Serial.println("==================================================");

  // 1. Initialize SX1278 LoRa Module over SPI
  LoRa.setPins(SS, RST, DIO0);
  if (!LoRa.begin(433E6)) { // 433 MHz Frequency
    Serial.println("❌ ERROR: LoRa initialization failed! Check wiring or antenna.");
    while (1) {
      delay(1000);
    }
  }

  // Set max RF output power (+20 dBm) for maximum long-range penetration
  LoRa.setTxPower(20);
  // Set Spreading Factor to SF9 for balanced speed and maximum sensitivity
  LoRa.setSpreadingFactor(9);
  // Set Signal Bandwidth to 125 kHz
  LoRa.setSignalBandwidth(125E3);
  
  Serial.println("✅ SX1278 LoRa Module Initialized at 433 MHz (+20 dBm Max Power)");

  // 2. Initialize Bluetooth BLE Server
  // Note: For Node 2 or Node 3, you can name them "ResQMesh-Node-2", "ResQMesh-Node-3"
  BLEDevice::init("ResQMesh-Node-1"); 
  BLEServer *pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  BLEService *pService = pServer->createService(SERVICE_UUID);

  // Characteristic for sending data to Android App (TX - Notify)
  pTxCharacteristic = pService->createCharacteristic(
                        CHARACTERISTIC_UUID_TX,
                        BLECharacteristic::PROPERTY_NOTIFY
                      );
  pTxCharacteristic->addDescriptor(new BLE2902());

  // Characteristic for receiving data from Android App (RX - Write)
  BLECharacteristic *pRxCharacteristic = pService->createCharacteristic(
                                           CHARACTERISTIC_UUID_RX,
                                           BLECharacteristic::PROPERTY_WRITE
                                         );
  pRxCharacteristic->setCallbacks(new MyCallbacks());

  // Start BLE Service & Advertising
  pService->start();
  BLEDevice::startAdvertising();

  Serial.println("✅ Bluetooth BLE Radio Active & Advertising as 'ResQMesh-Node-1'");
  Serial.println("==================================================");
  Serial.println("Waiting for incoming LoRa RF signals or BLE connection...\n");
}

// ---------------------------------------------------------------------------------
// Arduino Loop Function (Listens for incoming 433MHz LoRa packets from air)
// ---------------------------------------------------------------------------------
void loop() {
  // Check if an incoming LoRa packet was received over the air
  int packetSize = LoRa.parsePacket();
  if (packetSize) {
    String incoming = "";
    while (LoRa.available()) {
      incoming += (char)LoRa.read();
    }

    Serial.print("📡 LoRa Packet Received from Air (RSSI ");
    Serial.print(LoRa.packetRssi());
    Serial.print(" dBm): ");
    Serial.println(incoming);

    // If Android Phone is connected via BLE, forward incoming packet to the App!
    if (deviceConnected && incoming.length() > 0) {
      pTxCharacteristic->setValue(incoming.c_str());
      pTxCharacteristic->notify();
      Serial.println(">>> Forwarded LoRa packet to ResQMesh Android App via BLE! <<<");
    } else {
      Serial.println("⚠️ Phone not connected over BLE. Packet received but not sent to app.");
    }
  }
}