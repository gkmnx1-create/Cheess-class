# CHESS CLASS (GX)

Android için Türkçe satranç yardımcısı.

## Mevcut özellikler

- Android ekran yakalama (MediaProjection)
- Ekran üzerinde yüzen GX kontrolü ve hamle önerisi
- Satranç tahtasını bulma ve 8×8 kareleri örnekleme
- Tahta desenini hem parlaklık hem renk tonu farkıyla arama; aynı parlaklıktaki ama farklı renkteki kare temalarını da yakalamaya çalışma
- Başlangıç diziliminden taş görünümlerini kalibre etme
- Beyaz/siyah piyon, at, fil, kale, vezir ve şah sınıfları
- Kalibrasyon sırasında aynı taş türünün birden fazla örneğinden görsel prototip üretme
- FEN oluşturma ve Stockfish ile hamle analizi
- Öneriyi Türkçe taş adıyla gösterme (ör. “At e4”, “Fil c4”)

## Taş görünümü kalibrasyonu

Chess Clash'te taş setlerinin görselleri değişebildiği için sınıflandırıcı sabit tek bir Chess Clash görseline bağlı kalmaz. İlk kalibrasyon sırasında tahtadaki başlangıç dizilimini örnekler; her taş türü için aynı renkteki örneklerden görsel prototip çıkarır. Bu, farklı set görünümlerine uyum sağlamayı amaçlar.

**İlk kullanımda:** Chess Clash'i standart başlangıç konumunda açın, ekran yakalamayı başlatın ve kalibrasyonun tamamlanmasını bekleyin. Taş seti değişirse yeniden kalibrasyon yapılmalıdır.

Tahta bulucu artık yalnızca gri ton/parlaklık farkına bağlı değildir; karelerin renk tonu farkını da değerlendirir. Böylece farklı renk paletli tahta temalarına daha dayanıklı olması hedeflenir. Ancak desenli dokular, animasyonlar, parıltılar, tahta kenarlıkları, yakınlaştırma ve ekran ölçeklemesi hâlâ algılamayı etkileyebilir. Tüm Chess Clash temalarında doğruluk garantisi yoktur; gerçek ekran görüntüleriyle test edilip eşikler iyileştirilmelidir.

## Derleme

Projeyi Android Studio ile açın ve Gradle senkronizasyonunu tamamlayın. Uygulama henüz bu değişiklik sonrasında cihaz üzerinde derlenip test edilmiş bir APK olarak doğrulanmamıştır.

## Geliştirme notları

- Görsel sınıflandırma kodu: `app/src/main/java/com/chessclass/app/vision/PieceClassifier.java`
- Tahta tarama akışı: `app/src/main/java/com/chessclass/app/BoardVision.kt`
- Ekran yakalama: `app/src/main/java/com/chessclass/app/ScreenCaptureService.kt`
- Yüzen panel ve öneri: `app/src/main/java/com/chessclass/app/OverlayService.kt`


## Yapay zekâ ile 64 kare tanıma (hazırlık aşaması)

Android tarafında LiteRT/TensorFlow Lite çalışma zamanı ve `LiteRtSquareClassifier` eklendi. Modelin beklenen sözleşmesi:

- Dosya: `app/src/main/assets/gx_chess_64.tflite`
- Girdi: `[1, 64, 64, 3]`, RGB float32, 0–1 aralığı
- Çıktı: `[1, 13]`; sınıf sırası: boş, beyaz P/N/B/R/Q/K, siyah p/n/b/r/q/k
- Güven eşiği: 0.72; eşik altındaki kareler tahmin edilmez.

**Model dosyası henüz ekli değildir.** Bu nedenle uygulama şu an eğitilmiş yapay zekâ ile taş tanıdığı iddiasında bulunmaz; mevcut algılama yolu korunur. Model, gerçek Chess Clash ekran görüntülerinden etiketli 64-kare örnekleriyle eğitilip doğrulandıktan sonra assets klasörüne eklenmelidir. Veri kümesinde farklı tahta temaları, taş setleri, ekran ölçekleri ve oyun içi vurgular bulunmalıdır.
