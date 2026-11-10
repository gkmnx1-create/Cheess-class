# GX taş tanıma modeli eğitimi

Bu klasör, Android uygulamasının beklediği 64 kare / 13 sınıflı TFLite modelini üretir. Model henüz hazır değildir; gerçek oyun ekranlarından etiketli örnekler olmadan model eğitilmiş sayılmaz.

## Veri biçimi

Her dosya, Chess Clash tahtasından kesilmiş **tek bir kare** olmalıdır. Kare sınırları görünmeli; taş ortalanmalı. Aynı görüntünün kopyalarını eğitim ve doğrulama klasörlerine dağıtmayın.

```
training/dataset/
  train/
    empty/ P/ N/ B/ R/ Q/ K/ p/ n/ b/ r/ q/ k/
  val/
    empty/ P/ N/ B/ R/ Q/ K/ p/ n/ b/ r/ q/ k/
```

Sınıflar: empty=boş, büyük harfler=beyaz Piyon/At/Fil/Kale/Vezir/Şah, küçük harfler=siyah aynı taşlar. Her sınıfta en az 20 kare gerekir; pratikte yüzlerce veya binlerce örnek hedeflenmelidir.

## Veri toplama ilkeleri

- Farklı Chess Clash taş setleri ve tahta temaları.
- Farklı ekran çözünürlükleri, yakınlaştırma ve ışık/renk koşulları.
- Kare vurguları, seçili taşlar ve animasyonların ayrı örnekleri.
- Aynı ekran görüntüsünden kesilen kareleri train ve val arasında bölmeyin; ayrı oyun ekranlarını doğrulamaya ayırın.
- Her örneğin etiketi gerçek taş türü ve rengini göstermeli.

## Eğitim ve dışa aktarma

Python 3.10+ ve TensorFlow kurun:

```bash
python -m pip install tensorflow
python training/train_model.py
```

Başarılı doğrulama sonrası model otomatik olarak `app/src/main/assets/gx_chess_64.tflite` konumuna yazılır. Eğitim betiği doğrulama doğruluğu %90'ın altındaysa model dışa aktarmayı durdurur. %90 tek başına üretim doğruluğu garantisi değildir; her tema/taş seti ayrı test edilmelidir.
