/** Remove every APPn/COM segment, including EXIF/XMP/ICC, without trusting the client. */
export function stripJpeg(bytes: Uint8Array): Uint8Array {
  if (bytes.length < 4 || bytes.length > 2097152 || bytes[0] !== 255 || bytes[1] !== 216)
    throw new Error("JPEG no válido");
  const output: number[] = [255, 216];
  let i = 2, sawScan = false, sawFrame = false;
  while (i < bytes.length) {
    if (bytes[i++] !== 255) throw new Error("Marcador JPEG no válido");
    while (bytes[i] === 255) i++;
    const marker = bytes[i++];
    if (marker === 217) {
      if (!sawScan || !sawFrame) throw new Error("JPEG sin imagen");
      output.push(255, 217);
      return new Uint8Array(output); // discard trailing bytes too
    }
    if (marker === 0 || marker === 216 || (marker >= 208 && marker <= 215))
      throw new Error("Marcador inesperado");
    const length = (bytes[i] << 8) | bytes[i + 1];
    if (length < 2 || i + length > bytes.length) throw new Error("JPEG truncado");
    if (marker === 192 || marker === 194) {
      const height = (bytes[i + 3] << 8) | bytes[i + 4];
      const width = (bytes[i + 5] << 8) | bytes[i + 6];
      if (!width || !height || width > 1600 || height > 1600) throw new Error("Imagen demasiado grande");
      sawFrame = true;
    }
    if (!((marker >= 224 && marker <= 239) || marker === 254)) {
      output.push(255, marker);
      for (let k = i; k < i + length; k++) output.push(bytes[k]);
    }
    i += length;
    if (marker === 218) {
      sawScan = true;
      while (i < bytes.length) {
        if (bytes[i] === 255 && bytes[i + 1] !== 0 && !(bytes[i + 1] >= 208 && bytes[i + 1] <= 215)) break;
        output.push(bytes[i++]);
      }
    }
  }
  throw new Error("JPEG incompleto");
}
