#!/usr/bin/env python3
"""
Script para generar archivo TSV desde estructura de carpetas de imágenes.

Estructura esperada:
    dataset/
        0/
            img1.png
            img2.png
        1/
            img1.png
        ...
        9/
            img1.png

Genera un archivo TSV con formato:
    ruta_relativa<TAB>etiqueta
"""

import os
import sys
from pathlib import Path

def generate_tsv(dataset_folder, output_file):
    """Genera TSV desde carpetas de imágenes."""
    
    dataset_path = Path(dataset_folder).resolve()
    
    if not dataset_path.exists():
        print(f"ERROR: La carpeta no existe: {dataset_path}")
        sys.exit(1)
    
    entries = []
    image_extensions = {'.png', '.jpg', '.jpeg', '.bmp', '.gif'}
    
    # Recorrer subcarpetas (cada una es una clase)
    for class_folder in sorted(dataset_path.iterdir()):
        if not class_folder.is_dir():
            continue
        
        class_name = class_folder.name
        
        # Intentar usar el nombre de carpeta como etiqueta numérica
        try:
            label = int(class_name)
        except ValueError:
            label = class_name  # Si no es número, usar el nombre
        
        # Recorrer imágenes en la carpeta
        for img_file in sorted(class_folder.iterdir()):
            if img_file.is_file() and img_file.suffix.lower() in image_extensions:
                # Ruta relativa desde el dataset
                relative_path = img_file.relative_to(dataset_path)
                entries.append((str(relative_path).replace('\\', '/'), label))
    
    if not entries:
        print("ERROR: No se encontraron imágenes")
        sys.exit(1)
    
    # Escribir TSV (tabulación como separador)
    output_path = Path(output_file)
    with open(output_path, 'w', encoding='utf-8') as f:
        for path, label in entries:
            f.write(f"{path}\t{label}\n")
    
    print(f"Archivo generado: {output_path}")
    print(f"Total imágenes: {len(entries)}")
    print(f"Clases encontradas: {len(set(e[1] for e in entries))}")
    print()
    print("Primeras 5 líneas del archivo:")
    for path, label in entries[:5]:
        print(f"  {path}\t{label}")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Uso: python generate_image_tsv.py <carpeta_dataset> [archivo_salida.tsv]")
        print()
        print("Ejemplo:")
        print('  python generate_image_tsv.py "reduced training data" train.tsv')
        print('  python generate_image_tsv.py "reduced testing data" test.tsv')
        sys.exit(1)
    
    dataset_folder = sys.argv[1]
    output_file = sys.argv[2] if len(sys.argv) > 2 else "dataset.tsv"
    
    generate_tsv(dataset_folder, output_file)
