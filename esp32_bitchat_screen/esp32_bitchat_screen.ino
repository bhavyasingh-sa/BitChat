#define LGFX_USE_V1
#include <LovyanGFX.hpp>
#include <WiFi.h>
const char* WIFI_NAME     = "BitChat";
const char* WIFI_PASSWORD = "bitchat123";    
const int   WIFI_CHANNEL  = 6;
const int   MAX_USERS     = 8;
const bool  AUTO_START_NETWORK = true;              
const int  SCREEN_ROTATION     = 1;          
const int  TOUCH_OFFSET_ROT    = 2;          
const bool PANEL_INVERT_COLORS = false;      
const bool PANEL_BGR_ORDER     = false;     
const int PIN_TFT_SCLK = 14, PIN_TFT_MOSI = 13, PIN_TFT_MISO = 12;
const int PIN_TFT_DC = 2,    PIN_TFT_CS = 15,   PIN_TFT_BL = 27;
const int PIN_TOUCH_CS = 33;
const int PIN_BOOT_BUTTON = 0;
class LGFX : public lgfx::LGFX_Device {
  lgfx::Panel_ST7796  _panel;
  lgfx::Bus_SPI       _bus;
  lgfx::Light_PWM     _light;
  lgfx::Touch_XPT2046 _touch;
public:
  LGFX() {
    {
      auto cfg = _bus.config();
      cfg.spi_host    = SPI2_HOST;
      cfg.spi_mode    = 0;
      cfg.freq_write  = 40000000;
      cfg.freq_read   = 16000000;
      cfg.spi_3wire   = false;
      cfg.use_lock    = true;
      cfg.dma_channel = SPI_DMA_CH_AUTO;
      cfg.pin_sclk = PIN_TFT_SCLK;
      cfg.pin_mosi = PIN_TFT_MOSI;
      cfg.pin_miso = PIN_TFT_MISO;
      cfg.pin_dc   = PIN_TFT_DC;
      _bus.config(cfg);
      _panel.setBus(&_bus);
    }
    {
      auto cfg = _panel.config();
      cfg.pin_cs   = PIN_TFT_CS;
      cfg.pin_rst  = -1;
      cfg.pin_busy = -1;
      cfg.panel_width    = 320;
      cfg.panel_height   = 480;
      cfg.memory_width   = 320;
      cfg.memory_height  = 480;
      cfg.offset_x = 0;
      cfg.offset_y = 0;
      cfg.offset_rotation = 0;
      cfg.readable   = false;
      cfg.invert     = PANEL_INVERT_COLORS;
      cfg.rgb_order  = PANEL_BGR_ORDER;
      cfg.dlen_16bit = false;
      cfg.bus_shared = true;                
      _panel.config(cfg);
    }
    {
      auto cfg = _light.config();
      cfg.pin_bl      = PIN_TFT_BL;
      cfg.invert      = false;
      cfg.freq        = 44100;
      cfg.pwm_channel = 7;
      _light.config(cfg);
      _panel.setLight(&_light);
    }
    {
      auto cfg = _touch.config();
      cfg.x_min = 300;  cfg.x_max = 3900;
      cfg.y_min = 300;  cfg.y_max = 3900;
      cfg.pin_int  = -1;                    
      cfg.bus_shared = true;
      cfg.offset_rotation = TOUCH_OFFSET_ROT;
      cfg.spi_host = SPI2_HOST;             
      cfg.freq     = 1000000;
      cfg.pin_sclk = PIN_TFT_SCLK;
      cfg.pin_mosi = PIN_TFT_MOSI;
      cfg.pin_miso = PIN_TFT_MISO;
      cfg.pin_cs   = PIN_TOUCH_CS;
      _touch.config(cfg);
      _panel.setTouch(&_touch);
    }
    setPanel(&_panel);
  }
};

LGFX* lcd = nullptr;
bool networkOn = false;
int  shownCount = -1;
bool wasTouched = false;
bool wasBootDown = false;
unsigned long lastToggle = 0;
unsigned long lastPoll = 0;
uint16_t COL_HEADER, COL_GREEN, COL_RED, COL_BG, COL_TEXT, COL_GRAY, COL_PANEL;
const int BTN_X = 360, BTN_Y = 8, BTN_W = 110, BTN_H = 40;
const int TOUCH_ZONE_X = 340, TOUCH_ZONE_Y = 60;
void drawHeader() {
  lcd->fillRect(0, 0, 480, 56, COL_HEADER);
  lcd->setTextSize(1);
  lcd->setTextColor(TFT_WHITE, COL_HEADER);
  lcd->setFont(&fonts::FreeSansBold18pt7b);
  lcd->setTextDatum(middle_left);
  lcd->drawString("BitChat Network", 16, 28);
  uint16_t color = networkOn ? COL_RED : COL_GREEN;
  lcd->fillRoundRect(BTN_X, BTN_Y, BTN_W, BTN_H, 10, color);
  lcd->setTextColor(TFT_WHITE, color);
  lcd->setFont(&fonts::FreeSansBold12pt7b);
  lcd->setTextDatum(middle_center);
  lcd->drawString(networkOn ? "Stop" : "Start", BTN_X + BTN_W / 2, BTN_Y + BTN_H / 2);
}
void drawCount() {
  const int x = 280, y = 70, w = 190, h = 200;
  lcd->fillRoundRect(x, y, w, h, 16, COL_PANEL);
  lcd->setTextDatum(middle_center);
  lcd->setTextColor(COL_HEADER, COL_PANEL);
  lcd->setFont(&fonts::FreeSansBold24pt7b);
  lcd->setTextSize(2.5);
  lcd->drawString(String(WiFi.softAPgetStationNum()), x + w / 2, y + 85);
  lcd->setTextSize(1);
  lcd->setFont(&fonts::FreeSans12pt7b);
  lcd->setTextColor(COL_GRAY, COL_PANEL);
  lcd->drawString("devices connected", x + w / 2, y + 170);
}
void drawInfo() {
  lcd->fillRect(0, 60, 480, 224, COL_BG);
  lcd->setTextSize(1);
  if (!networkOn) {
    lcd->setTextDatum(middle_center);
    lcd->setTextColor(COL_TEXT, COL_BG);
    lcd->setFont(&fonts::FreeSansBold24pt7b);
    lcd->drawString("Network is OFF", 240, 135);
    lcd->setFont(&fonts::FreeSans12pt7b);
    lcd->setTextColor(COL_GRAY, COL_BG);
    lcd->drawString("Tap Start in the top corner", 240, 195);
    lcd->drawString("or press the BOOT button", 240, 225);
    return;
  }
  const int x = 20;
  lcd->setTextDatum(top_left);
  lcd->setFont(&fonts::FreeSans12pt7b);
  lcd->setTextColor(COL_GRAY, COL_BG);
  lcd->drawString("Wi-Fi name", x, 70);
  lcd->drawString("Password", x, 130);
  lcd->drawString("Server address", x, 190);
  lcd->setFont(&fonts::FreeSansBold18pt7b);
  lcd->setTextColor(COL_TEXT, COL_BG);
  lcd->drawString(WIFI_NAME, x, 92);
  lcd->drawString(WIFI_PASSWORD, x, 152);
  lcd->drawString(WiFi.softAPIP().toString(), x, 212);
  drawCount();
}
void drawHint() {
  lcd->fillRect(0, 285, 480, 35, COL_BG);
  lcd->setTextSize(1);
  lcd->setFont(&fonts::FreeSans9pt7b);
  lcd->setTextColor(COL_GRAY, COL_BG);
  lcd->setTextDatum(middle_left);
  lcd->drawString("Laptops join this Wi-Fi, then run the Java Server / Client", 12, 303);
}
void drawAll() {
  lcd->fillScreen(COL_BG);
  drawHeader();
  drawInfo();
  drawHint();
}
void startNetwork() {
  WiFi.mode(WIFI_AP);
  networkOn = WiFi.softAP(WIFI_NAME, WIFI_PASSWORD, WIFI_CHANNEL, 0, MAX_USERS);
  Serial.println(networkOn ? "Network ON" : "Could not start the network");
  if (networkOn) {
    Serial.print("  Wi-Fi: "); Serial.print(WIFI_NAME);
    Serial.print("   IP: ");    Serial.println(WiFi.softAPIP());
  }
}
void stopNetwork() {
  WiFi.softAPdisconnect(true);
  WiFi.mode(WIFI_OFF);
  networkOn = false;
  Serial.println("Network OFF");
}
void toggleNetwork() {
  if (millis() - lastToggle < 500) return;     
  lastToggle = millis();
  if (networkOn) stopNetwork(); else startNetwork();
  shownCount = -1;
  drawHeader();
  drawInfo();
}
void setup() {
  Serial.begin(115200);
  delay(300);
  Serial.println();
  Serial.println("BitChat touchscreen network starting...");
  pinMode(PIN_BOOT_BUTTON, INPUT_PULLUP);
  lcd = new LGFX();
  lcd->init();
  lcd->setRotation(SCREEN_ROTATION);
  lcd->setBrightness(255);
  COL_HEADER = lcd->color565(7, 94, 84);
  COL_GREEN  = lcd->color565(37, 160, 90);
  COL_RED    = lcd->color565(200, 50, 50);
  COL_BG     = lcd->color565(255, 255, 255);
  COL_TEXT   = lcd->color565(20, 20, 20);
  COL_GRAY   = lcd->color565(110, 110, 110);
  COL_PANEL  = lcd->color565(228, 245, 236);
  drawAll();
  if (AUTO_START_NETWORK) {
    startNetwork();
    shownCount = -1;
    drawHeader();
    drawInfo();
  }
}
void loop() {
  int32_t tx, ty;
  bool touched = lcd->getTouch(&tx, &ty);
  if (touched && !wasTouched) {
    Serial.printf("Touch at x=%d y=%d\n", (int)tx, (int)ty);
    if (tx >= TOUCH_ZONE_X && ty <= TOUCH_ZONE_Y) {
      toggleNetwork();
    }
  }
  wasTouched = touched;
  bool bootDown = (digitalRead(PIN_BOOT_BUTTON) == LOW);
  if (bootDown && !wasBootDown) toggleNetwork();
  wasBootDown = bootDown;
  if (networkOn && millis() - lastPoll > 500) {
    lastPoll = millis();
    int count = WiFi.softAPgetStationNum();
    if (count != shownCount) {
      shownCount = count;
      drawCount();
      Serial.printf("Devices connected: %d\n", count);
    }
  }
  delay(10);
}
