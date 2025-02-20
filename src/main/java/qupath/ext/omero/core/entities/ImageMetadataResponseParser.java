package qupath.ext.omero.core.entities;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import qupath.ext.omero.core.apis.ApisHandler;
import qupath.lib.common.ColorTools;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelType;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Reads the response from an image metadata request done to
 * {@code <omero_web_address>/webgateway/imgData/<image_id>} and parse it.
 */
public class ImageMetadataResponseParser {

    private static final Logger logger = LoggerFactory.getLogger(ImageMetadataResponseParser.class);

    private ImageMetadataResponseParser() {
        throw new AssertionError("This class is not instantiable.");
    }

    /**
     * Parse a metadata response.
     *
     * @param metadataResponse a JSON containing metadata to parse
     * @return the parsed metadata
     * @throws IllegalArgumentException when it was not possible to parse image width, image height,
     * channels or pixel type from the provided JSON object
     */
    public static ImageServerMetadata createMetadataFromJson(JsonObject metadataResponse) {
        ImageServerMetadata.Builder metadataBuilder = new ImageServerMetadata.Builder();

        setImageSize(metadataResponse, metadataBuilder);
        setPixelSize(metadataResponse, metadataBuilder);

        List<ImageChannel> channels = setChannels(metadataResponse, metadataBuilder);
        PixelType pixelType = setImageNameAndPixelType(metadataResponse, metadataBuilder);

        metadataBuilder.rgb(channels.size() == 3 && pixelType == PixelType.UINT8);

        setTileSize(metadataResponse, metadataBuilder);
        setLevels(metadataResponse, metadataBuilder);

        getNumberFromJsonObject(metadataResponse, "nominalMagnification", logger.atDebug()).ifPresent(number ->
                metadataBuilder.magnification(number.doubleValue())
        );

        return metadataBuilder.build();
    }

    private static void setImageSize(JsonObject metadataResponse, ImageServerMetadata.Builder metadataBuilder) {
        if (!metadataResponse.has("size") || !metadataResponse.get("size").isJsonObject()) {
            throw new IllegalArgumentException(String.format("'size' JSON object not found in %s", metadataResponse));
        }
        JsonObject size = metadataResponse.getAsJsonObject("size");

        metadataBuilder.width(getNumberFromJsonObject(size, "width").intValue());
        metadataBuilder.height(getNumberFromJsonObject(size, "height").intValue());

        getNumberFromJsonObject(size, "z", logger.atDebug()).ifPresent(number -> metadataBuilder.sizeZ(number.intValue()));
        getNumberFromJsonObject(size, "t", logger.atDebug()).ifPresent(number -> metadataBuilder.sizeT(number.intValue()));
    }

    private static void setPixelSize(JsonObject metadataResponse, ImageServerMetadata.Builder metadataBuilder) {
        if (metadataResponse.has("pixel_size") && metadataResponse.get("pixel_size").isJsonObject()) {
            JsonObject pixelSize = metadataResponse.getAsJsonObject("pixel_size");

            if (pixelSize.has("x") && pixelSize.get("x").isJsonPrimitive() && pixelSize.getAsJsonPrimitive("x").isNumber() &&
                    pixelSize.has("y") && pixelSize.get("y").isJsonPrimitive() && pixelSize.getAsJsonPrimitive("y").isNumber()
            ) {
                metadataBuilder.pixelSizeMicrons(
                        pixelSize.getAsJsonPrimitive("x").getAsNumber(),
                        pixelSize.getAsJsonPrimitive("y").getAsNumber()
                );
            } else {
                logger.debug("'x' or 'y' number not found in {}", pixelSize);
            }

            if (pixelSize.has("z") && pixelSize.get("z").isJsonPrimitive() && pixelSize.getAsJsonPrimitive("z").isNumber()) {
                metadataBuilder.zSpacingMicrons(pixelSize.getAsJsonPrimitive("z").getAsNumber());
            } else {
                logger.debug("'z' number not found in {}", pixelSize);
            }
        } else {
            logger.debug("'pixel_size' JSON object not found in {}", metadataResponse);
        }
    }

    private static List<ImageChannel> setChannels(JsonObject metadataResponse, ImageServerMetadata.Builder metadataBuilder) {
        if (!metadataResponse.has("channels") || !metadataResponse.get("channels").isJsonArray() ||
                metadataResponse.getAsJsonArray("channels").isEmpty()
        ) {
            throw new IllegalArgumentException(String.format("Non empty 'channels' JSON array not found in %s", metadataResponse));
        }
        List<JsonElement> channelsJson = metadataResponse.getAsJsonArray("channels").asList();

        List<ImageChannel> channels = IntStream.range(0, channelsJson.size())
                .mapToObj(i -> {
                    if (!channelsJson.get(i).isJsonObject()) {
                        throw new IllegalArgumentException(String.format("The JSON element %s is not a JSON object", channelsJson.get(i)));
                    }
                    JsonObject channel = channelsJson.get(i).getAsJsonObject();

                    String channelName;
                    if (channel.has("label") && channel.get("label").isJsonPrimitive()) {
                        channelName = channel.get("label").getAsString();
                    } else {
                        logger.debug("'label' string not found in {}", channel);
                        channelName = String.format("Channel %d", i);
                    }

                    Integer color;
                    if (channel.has("color") && channel.get("color").isJsonPrimitive()) {
                        String colorText = channelsJson.get(i).getAsJsonObject().get("color").getAsString();

                        try {
                            color = ColorTools.packRGB(
                                    Integer.valueOf(colorText.substring(0, 2), 16),
                                    Integer.valueOf(colorText.substring(2, 4), 16),
                                    Integer.valueOf(colorText.substring(4, 6), 16)
                            );
                        } catch (IndexOutOfBoundsException |  NumberFormatException e) {
                            logger.debug("Could not parse a color from {}", colorText, e);
                            color = ImageChannel.getDefaultChannelColor(i);
                        }
                    } else {
                        logger.debug("'color' string not found in {}", channel);
                        color = ImageChannel.getDefaultChannelColor(i);
                    }

                    return ImageChannel.getInstance(
                            channelName,
                            color
                    );
                })
                .toList();

        metadataBuilder.channels(channels);
        return channels;
    }

    private static PixelType setImageNameAndPixelType(JsonObject metadataResponse, ImageServerMetadata.Builder metadataBuilder) {
        if (!metadataResponse.has("meta") || !metadataResponse.get("meta").isJsonObject()) {
            throw new IllegalArgumentException(String.format("'meta' JSON object not found in %s", metadataResponse));
        }
        JsonObject meta = metadataResponse.getAsJsonObject("meta");

        if (meta.has("imageName") && meta.get("imageName").isJsonPrimitive()) {
            metadataBuilder.name(meta.get("imageName").getAsString());
        } else {
            logger.debug("'imageName' string not found in {}", meta);
        }

        if (!meta.has("pixelsType") || !meta.get("pixelsType").isJsonPrimitive()) {
            throw new IllegalArgumentException(String.format("'pixelsType' string not found in %s", meta));
        }
        String pixelsType = meta.get("pixelsType").getAsString();

        PixelType pixelType = ApisHandler.getPixelType(pixelsType).orElseThrow(() -> new IllegalArgumentException(
                String.format("Cannot convert %s to a known pixel type", pixelsType)
        ));
        metadataBuilder.pixelType(pixelType);

        return pixelType;

    }

    private static void setTileSize(JsonObject metadataResponse, ImageServerMetadata.Builder metadataBuilder) {
        if (metadataResponse.has("tile_size") && metadataResponse.get("tile_size").isJsonObject()) {
            JsonObject tileSize = metadataResponse.getAsJsonObject("tile_size");

            Optional<Number> tileWidth = getNumberFromJsonObject(tileSize, "width", logger.atDebug());
            Optional<Number> tileHeight = getNumberFromJsonObject(tileSize, "height", logger.atDebug());

            if (tileWidth.isPresent() && tileHeight.isPresent()) {
                metadataBuilder.preferredTileSize(tileWidth.get().intValue(), tileHeight.get().intValue());
            }
        } else {
            logger.debug("'tile_size' JSON object not found in {}", metadataResponse);
        }
    }

    private static void setLevels(JsonObject metadataResponse, ImageServerMetadata.Builder metadataBuilder) {
        Optional<Number> levels = getNumberFromJsonObject(metadataResponse, "levels", logger.atDebug());

        if (levels.isPresent() && levels.get().intValue() > 1) {
            if (metadataResponse.has("zoomLevelScaling") && metadataResponse.get("zoomLevelScaling").isJsonObject()) {
                JsonObject zoom = metadataResponse.getAsJsonObject("zoomLevelScaling");

                metadataBuilder.levelsFromDownsamples(IntStream.range(0, levels.get().intValue())
                        .mapToObj(i -> getNumberFromJsonObject(zoom, Integer.toString(i), logger.atDebug()))
                        .flatMap(Optional::stream)
                        .mapToDouble(z -> 1.0 / z.doubleValue())
                        .toArray()
                );
            } else {
                logger.debug("'zoomLevelScaling' JSON object not found in {}", metadataResponse);
            }
        }
    }

    private static Number getNumberFromJsonObject(JsonObject jsonObject, String attributeName) {
        if (jsonObject.has(attributeName) && jsonObject.get(attributeName).isJsonPrimitive() && jsonObject.getAsJsonPrimitive(attributeName).isNumber()) {
            return jsonObject.getAsJsonPrimitive(attributeName).getAsNumber();
        } else {
            throw new IllegalArgumentException(String.format("'%s' number not found in %s", attributeName, jsonObject));
        }
    }

    private static Optional<Number> getNumberFromJsonObject(JsonObject jsonObject, String attributeName, LoggingEventBuilder logger) {
        try {
            return Optional.of(getNumberFromJsonObject(jsonObject, attributeName));
        } catch (IllegalArgumentException e) {
            logger.log(e.getMessage());
            return Optional.empty();
        }
    }
}
