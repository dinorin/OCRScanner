# OCR Scanner

Android app quét và nhận diện chữ theo thời gian thực bằng camera, hỗ trợ chụp ảnh để phân tích chi tiết.

## Tính năng

- **Quét realtime** — nhận diện chữ liên tục qua camera, hiển thị text và chip kết quả ngay lập tức
- **Chụp ảnh phân tích** — chụp frame hiện tại, OCR lại trên ảnh tĩnh với bounding box từng dòng
- **Chọn ảnh từ thư viện** — import ảnh có sẵn để phân tích
- **Tap chọn từng dòng** — nhấn vào bounding box để mở panel text có thể select/copy
- **Kéo chọn vùng** — drag rectangle để lấy toàn bộ chữ trong vùng đó
- **Pinch-to-zoom** trên ảnh đã chụp, double-tap để reset
- **Zoom camera** bằng pinch, tap để lấy nét tại điểm bất kỳ
- **Flash / đèn pin** toggle
- **Grid rule-of-thirds** overlay toggle
- **Regex extractor** — tự động nhận diện số điện thoại, email, URL, ngày tháng, CCCD, số

## Screenshots

| Camera | Chụp ảnh | Chọn vùng |
|--------|----------|-----------|
| *(camera view với chip kết quả)* | *(ảnh tĩnh với bounding box)* | *(drag select rectangle)* |

## Tech stack

| Layer | Thư viện |
|---|---|
| UI | Jetpack Compose + Material3 |
| Camera | CameraX 1.4.0 |
| OCR | ML Kit Text Recognition 16.0.1 |
| State | ViewModel + StateFlow |
| Language | Kotlin 2.0.21 |

## Yêu cầu

- Android 7.0+ (API 24)
- Camera (bắt buộc)

## Cài đặt

1. Clone repo
2. Mở bằng Android Studio Ladybug trở lên
3. Build & chạy trên thiết bị thật (camera không hoạt động trên emulator)

```bash
git clone https://github.com/dinorin/OCRScanner.git
```

## Cấu trúc

```
app/src/main/java/com/dinorin/ocrscanner/
├── camera/
│   └── CameraPreviewView.kt      # CameraX setup, pinch-zoom, tap-to-focus
├── ocr/
│   ├── TextRecognitionAnalyzer.kt # ML Kit analyzer, FPS tracking
│   └── OcrResult.kt               # Data model
├── utils/
│   └── RegexExtractor.kt          # Regex patterns
├── viewmodel/
│   └── ScannerViewModel.kt        # State management
└── ui/screen/
    ├── ScannerScreen.kt            # Camera UI chính
    └── CapturedImageScreen.kt      # Xem & chọn text trên ảnh
```

## License

MIT
