#!/usr/bin/env python3
"""Train GX's 64-square classifier and export the Android TFLite asset.

Dataset layout:
  training/dataset/train/{empty, P, N, B, R, Q, K, p, n, b, r, q, k}/*.png
  training/dataset/val/{same class folders}/*.png

Each image must be a cropped single chessboard square (piece centered, full square
visible). The class names and order are the exact contract used by LiteRtSquareClassifier.
"""
from pathlib import Path
import tensorflow as tf

ROOT = Path(__file__).resolve().parent
TRAIN = ROOT / "dataset" / "train"
VAL = ROOT / "dataset" / "val"
OUT = ROOT.parent / "app" / "src" / "main" / "assets" / "gx_chess_64.tflite"
CLASSES = ["empty", "P", "N", "B", "R", "Q", "K", "p", "n", "b", "r", "q", "k"]
IMG = (64, 64)
BATCH = 64
EPOCHS = 35
SEED = 42

def check_dataset():
    for folder in (TRAIN, VAL):
        if not folder.is_dir():
            raise SystemExit(f"Dataset folder missing: {folder}")
        missing = [name for name in CLASSES if not (folder / name).is_dir()]
        if missing:
            raise SystemExit(f"{folder}: missing class folders: {', '.join(missing)}")
        counts = {name: len(list((folder / name).glob('*'))) for name in CLASSES}
        too_small = [f"{k}={v}" for k, v in counts.items() if v < 20]
        if too_small:
            raise SystemExit(f"Need at least 20 images per class in {folder}: {', '.join(too_small)}")

def load_split(path, shuffle):
    ds = tf.keras.utils.image_dataset_from_directory(
        path, class_names=CLASSES, label_mode="int", image_size=IMG,
        batch_size=BATCH, shuffle=shuffle, seed=SEED
    )
    return ds.map(lambda x, y: (tf.cast(x, tf.float32) / 255.0, y),
                  num_parallel_calls=tf.data.AUTOTUNE).prefetch(tf.data.AUTOTUNE)

def main():
    check_dataset()
    train_ds = load_split(TRAIN, True)
    val_ds = load_split(VAL, False)
    augmentation = tf.keras.Sequential([
        tf.keras.layers.RandomRotation(0.04),
        tf.keras.layers.RandomZoom(0.08),
        tf.keras.layers.RandomTranslation(0.05, 0.05),
        tf.keras.layers.RandomContrast(0.12),
    ])
    inputs = tf.keras.Input(shape=(64, 64, 3), name="rgb")
    x = augmentation(inputs)
    for filters in (32, 64, 96):
        x = tf.keras.layers.Conv2D(filters, 3, padding="same", use_bias=False)(x)
        x = tf.keras.layers.BatchNormalization()(x)
        x = tf.keras.layers.ReLU()(x)
        x = tf.keras.layers.MaxPooling2D()(x)
    x = tf.keras.layers.GlobalAveragePooling2D()(x)
    x = tf.keras.layers.Dropout(0.25)(x)
    outputs = tf.keras.layers.Dense(13, activation="softmax", name="class_probabilities")(x)
    model = tf.keras.Model(inputs, outputs)
    model.compile(optimizer=tf.keras.optimizers.Adam(1e-3),
                  loss="sparse_categorical_crossentropy", metrics=["accuracy"])
    callbacks = [
        tf.keras.callbacks.EarlyStopping(monitor="val_accuracy", patience=7,
                                         restore_best_weights=True),
        tf.keras.callbacks.ReduceLROnPlateau(monitor="val_loss", patience=3, factor=0.5),
    ]
    model.fit(train_ds, validation_data=val_ds, epochs=EPOCHS, callbacks=callbacks)
    loss, accuracy = model.evaluate(val_ds, verbose=0)
    print(f"Validation accuracy: {accuracy:.4f}; loss: {loss:.4f}")
    if accuracy < 0.90:
        raise SystemExit("Validation accuracy is below 90%; do not ship this model. Improve the dataset.")
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite = converter.convert()
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes(tflite)
    print(f"Exported: {OUT}")

if __name__ == "__main__":
    main()
