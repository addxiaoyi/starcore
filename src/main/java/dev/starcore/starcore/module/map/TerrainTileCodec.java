package dev.starcore.starcore.module.map;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TerrainTileCodec {
    private TerrainTileCodec() {
    }

    static byte[] encodePng(TerrainTileRaster raster) throws IOException {
        BufferedImage image = new BufferedImage(raster.tileSize(), raster.tileSize(), BufferedImage.TYPE_INT_ARGB);
        int[] colors = raster.colors();
        for (int py = 0; py < raster.tileSize(); py++) {
            for (int px = 0; px < raster.tileSize(); px++) {
                image.setRGB(px, py, colors[py * raster.tileSize() + px]);
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream(8192);
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    static String encodeJson(TerrainTileRaster raster) {
        TerrainTilePalette palette = terrainTilePalette(raster.colors());
        StringBuilder builder = new StringBuilder(48_000);
        builder.append('{');
        appendNumberField(builder, "version", 1);
        builder.append(',');
        appendField(builder, "format", "starcore-terrain-raster-v1");
        builder.append(',');
        appendField(builder, "world", raster.world());
        builder.append(',');
        appendNumberField(builder, "x", raster.minX());
        builder.append(',');
        appendNumberField(builder, "z", raster.minZ());
        builder.append(',');
        appendNumberField(builder, "worldSize", raster.worldSize());
        builder.append(',');
        appendNumberField(builder, "tileSize", raster.tileSize());
        builder.append(',');
        appendNumberField(builder, "heightMin", raster.heightMin());
        builder.append(',');
        appendNumberField(builder, "heightMax", raster.heightMax());
        builder.append(',');
        appendNumberField(builder, "paletteBits", 16);
        builder.append(',');
        builder.append("\"palette\":[");
        for (int i = 0; i < palette.colors().size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(palette.colors().get(i));
        }
        builder.append(']');
        builder.append(',');
        appendField(builder, "pixels", Base64.getEncoder().encodeToString(palette.indices()));
        builder.append(',');
        appendField(builder, "heights", Base64.getEncoder().encodeToString(encodeTerrainHeights(raster.heights())));
        builder.append(',');
        appendField(builder, "lights", Base64.getEncoder().encodeToString(raster.lights()));
        builder.append('}');
        return builder.toString();
    }

    static byte[] encodeBinary(TerrainTileRaster raster) {
        TerrainTilePalette palette = terrainTilePalette(raster.colors());
        int pixelCount = raster.tileSize() * raster.tileSize();
        ByteArrayOutputStream output = new ByteArrayOutputStream(36 + palette.colors().size() * 3 + pixelCount * 2);
        output.write('S');
        output.write('C');
        output.write('T');
        output.write('B');
        output.write(1);
        output.write(16);
        output.write(0);
        output.write(0);
        writeInt(output, raster.tileSize());
        writeInt(output, raster.worldSize());
        writeInt(output, raster.minX());
        writeInt(output, raster.minZ());
        writeInt(output, raster.heightMin());
        writeInt(output, raster.heightMax());
        writeInt(output, palette.colors().size());
        for (int color : palette.colors()) {
            output.write((color >> 16) & 0xff);
            output.write((color >> 8) & 0xff);
            output.write(color & 0xff);
        }
        output.writeBytes(palette.indices());
        return output.toByteArray();
    }

    private static void writeInt(ByteArrayOutputStream output, int value) {
        output.write((value >> 24) & 0xff);
        output.write((value >> 16) & 0xff);
        output.write((value >> 8) & 0xff);
        output.write(value & 0xff);
    }

    private static TerrainTilePalette terrainTilePalette(int[] colors) {
        Map<Integer, Integer> colorToIndex = new LinkedHashMap<>();
        List<Integer> palette = new ArrayList<>();
        byte[] indices = new byte[colors.length * 2];
        for (int i = 0; i < colors.length; i++) {
            int color = colors[i] & 0x00ffffff;
            Integer index = colorToIndex.get(color);
            if (index == null) {
                index = palette.size();
                colorToIndex.put(color, index);
                palette.add(color);
            }
            indices[i * 2] = (byte) ((index >> 8) & 0xff);
            indices[i * 2 + 1] = (byte) (index & 0xff);
        }
        return new TerrainTilePalette(palette, indices);
    }

    private static byte[] encodeTerrainHeights(int[] heights) {
        byte[] encoded = new byte[heights.length * 2];
        for (int i = 0; i < heights.length; i++) {
            int height = heights[i] == Integer.MIN_VALUE ? Short.MIN_VALUE : heights[i];
            encoded[i * 2] = (byte) ((height >> 8) & 0xff);
            encoded[i * 2 + 1] = (byte) (height & 0xff);
        }
        return encoded;
    }

    private static void appendField(StringBuilder builder, String name, String value) {
        builder.append('"').append(escape(name)).append("\":\"").append(escape(value)).append('"');
    }

    private static void appendNumberField(StringBuilder builder, String name, int value) {
        builder.append('"').append(escape(name)).append("\":").append(value);
    }

    private static String escape(String input) {
        return (input == null ? "" : input)
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t");
    }

    private record TerrainTilePalette(List<Integer> colors, byte[] indices) {
    }
}
