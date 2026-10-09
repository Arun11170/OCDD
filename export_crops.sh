#!/bin/bash
# Run INSIDE WSL:  source ~/et/bin/activate; bash /mnt/d/OCDD/export_crops.sh
for c in apple banana capsicum chilli grape groundnut maize mango okra potato rice sugarcane tomato wheat; do
  echo "=== export $c ==="
  OCDD_RUN=crop_$c OCDD_INTERIM=interim_v3 OCDD_OUT=export_crops/$c python /mnt/d/OCDD/export_onnx.py
done
