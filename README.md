# CHESS CLASS (GX)

Android için Türkçe satranç konum algılama ve hamle analiz yardımcısı.

## Uygulama akışı

1. Kullanıcı G panelinden ekran yakalamayı başlatır.
2. Android MediaProjection ile ekran görüntüsü alınır.
3. Tahta otomatik bulunur ve 64 kare ONNX modeliyle sınıflandırılır.
4. Konum doğrulandıktan sonra FEN oluşturulur.
5. Stockfish 19 Android ARM64 motoru en iyi hamleyi hesaplar.
6. G panelinin yanındaki bilgi alanında öneri Türkçe taş adı ve hedef kareyle gösterilir (ör. “At e4”, “Fil c4”).

## Görüntü tanıma

- Tahta algılama: `vision/BoardDetector.java`
- Kare örnekleme ve eski kalibrasyon yedeği: `vision/PieceClassifier.java`
- Ana tarama ve hamle sırası: `BoardVision.kt`
- ONNX sınıflandırıcı: `vision/LiteRtSquareClassifier.kt`
- Model dosyası: CI derlemesinde Fenshot deposundan `chess-tiles-v2.onnx` olarak indirilir.
- Model girişi: float32 `[64, 1024]`, 32×32 gri tonlu kareler.
- Model çıkışı: `[64, 13]`, etiket sırası `1KQRBNPkqrbnp`.
- Güven eşiği: 0.70.

İlk görüntüde hamle sırası statik ekran görüntüsünden kesin belirlenemediği için ilk öneri geçici olarak işaretlenir. Gerçek hamle algılandığında sıra takibi başlar. Tahta bulucu düz, kare biçimli ve perspektif bozulması olmayan tahtaları hedefler; perspektif düzeltme henüz yoktur.

## Performans

- Ekran kareleri ve tahta/ONNX analizi ana iş parçacığından ayrı yürütülür.
- Tahta aramasının yoğun puanlama yolunda geçici dizi tahsisleri azaltılmıştır.
- Ekran yakalama 720 piksel uzun kenarla sınırlandırılır ve yaklaşık saniyede bir analiz yapılır.

## Derleme

GitHub Actions, Java 17 ile `gradle assembleDebug` çalıştırır; Stockfish ARM64 motorunu ve ONNX modelini derleme sırasında indirir. APK, başarılı Actions çalışmasının `chess-class-debug-apk` artifact'inde bulunur.

## Önemli sınırlar

Kodun derlenmesi, gerçek oyun ekranlarında taşların hatasız tanınacağını garanti etmez. Tahta temaları, taş setleri, animasyonlar, vurgular ve ekran ölçekleri gerçek cihazda ayrıca test edilmelidir. FEN doğrulaması temel yapısal kontroller uygular; tam bir yasal satranç konumu doğrulayıcısı değildir.
