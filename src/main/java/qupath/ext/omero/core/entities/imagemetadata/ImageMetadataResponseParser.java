package qupath.ext.omero.core.entities.imagemetadata;

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
     * @return the parsed metadata, or an empty Optional if one the following parameter was not found:
     * image width, image height, channels, pixel type
     */
    public static Optional<ImageServerMetadata> createMetadataFromJson(JsonObject metadataResponse) {
        ImageServerMetadata.Builder metadataBuilder = new ImageServerMetadata.Builder();

        if (!setImageSize(metadataResponse, metadataBuilder)) {
            return Optional.empty();
        }

        setPixelSize(metadataResponse, metadataBuilder);

        List<ImageChannel> channels = setChannels(metadataResponse, metadataBuilder);
        if (channels.isEmpty()) {
            return Optional.empty();
        }

        Optional<PixelType> pixelType = setImageNameAndPixelType(metadataResponse, metadataBuilder);
        if (pixelType.isEmpty()) {
            return Optional.empty();
        }

        metadataBuilder.rgb(channels.stream().map(ImageChannel::getColor).toList().equals(List.of(ColorTools.RED, ColorTools.GREEN, ColorTools.BLUE)) &&
                pixelType.get() == PixelType.UINT8
        );

        setTileSize(metadataResponse, metadataBuilder);
        setLevels(metadataResponse, metadataBuilder);

        getNumberFromJsonObject(metadataResponse, "nominalMagnification", logger.atDebug()).ifPresent(number ->
                metadataBuilder.magnification(number.doubleValue())
        );

        return Optional.of(metadataBuilder.build());
    }

    private static boolean setImageSize(JsonObject metadataResponse, ImageServerMetadata.Builder metadataBuilder) {
        if (!metadataResponse.has("size") || !metadataResponse.get("size").isJsonObject()) {
            logger.error(String.format("'size' JSON object not found in %s", metadataResponse));
            return false;
        }
        JsonObject size = metadataResponse.getAsJsonObject("size");

        Optional<Number> sizeX = getNumberFromJsonObject(size, "width", logger.atError());
        if (sizeX.isEmpty()) {
            return false;
        } else {
            metadataBuilder.width(sizeX.get().intValue());
        }

        Optional<Number> sizeY = getNumberFromJsonObject(size, "height", logger.atError());
        if (sizeY.isEmpty()) {
            return false;
        } else {
            metadataBuilder.height(sizeY.get().intValue());
        }

        getNumberFromJsonObject(size, "z", logger.atDebug()).ifPresent(number -> metadataBuilder.sizeZ(number.intValue()));
        getNumberFromJsonObject(size, "t", logger.atDebug()).ifPresent(number -> metadataBuilder.sizeT(number.intValue()));

        return true;
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
                logger.debug(String.format("'x' or 'y' number not found in %s", pixelSize));
            }

            if (pixelSize.has("z") && pixelSize.get("z").isJsonPrimitive() && pixelSize.getAsJsonPrimitive("z").isNumber()) {
                metadataBuilder.zSpacingMicrons(pixelSize.getAsJsonPrimitive("z").getAsNumber());
            } else {
                logger.debug(String.format("'z' number not found in %s", pixelSize));
            }
        } else {
            logger.debug(String.format("'pixel_size' JSON object not found in %s", metadataResponse));
        }
    }

    private static List<ImageChannel> setChannels(JsonObject metadataResponse, ImageServerMetadata.Builder metadataBuilder) {
        if (metadataResponse.has("channels") && metadataResponse.get("channels").isJsonArray()) {
            List<JsonElement> channelsJson = metadataResponse.getAsJsonArray("channels").asList();

            List<ImageChannel> channels = IntStream.range(0, channelsJson.size())
                    .mapToObj(i -> {
                        if (channelsJson.get(i).isJsonObject()) {
                            JsonObject channel = channelsJson.get(i).getAsJsonObject();

                            if (channel.has("color") && channel.get("color").isJsonPrimitive()
                                    && channel.has("label") && channel.get("label").isJsonPrimitive()
                            ) {
                                String color = channelsJson.get(i).getAsJsonObject().get("color").getAsString();

                                try {
                                    return ImageChannel.getInstance(
                                            channel.get("label").getAsString(),
                                            ColorTools.packRGB(
                                                    Integer.valueOf(color.substring(0, 2), 16),
                                                    Integer.valueOf(color.substring(2, 4), 16),
                                                    Integer.valueOf(color.substring(4, 6), 16)
                                            )
                                    );
                                } catch (IndexOutOfBoundsException |  NumberFormatException e) {
                                    logger.debug(String.format("Could not parse a color from %s", color), e);
                                }
                            } else {
                                logger.debug(String.format("'color' or 'label' string not found in %s", channel));
                            }
                        } else {
                            logger.debug(String.format("The JSON element %s is not a JSON object", channelsJson.get(i)));
                        }

                        return ImageChannel.getInstance(
                                "Channel " + i,
                                ImageChannel.getDefaultChannelColor(i)
                        );
                    })
                    .toList();

            metadataBuilder.channels(channels);
            return channels;
        } else {
            logger.error(String.format("'channels' JSON array not found in %s", metadataResponse));
            return List.of();
        }
    }

    private static Optional<PixelType> setImageNameAndPixelType(JsonObject metadataResponse, ImageServerMetadata.Builder metadataBuilder) {
        if (metadataResponse.has("meta") && metadataResponse.get("meta").isJsonObject()) {
            JsonObject meta = metadataResponse.getAsJsonObject("meta");

            if (meta.has("imageName") && meta.get("imageName").isJsonPrimitive()) {
                metadataBuilder.name(meta.get("imageName").getAsString());
            } else {
                logger.debug(String.format("'imageName' string not found in %s", meta));
            }

            if (meta.has("pixelsType") && meta.get("pixelsType").isJsonPrimitive()) {
                Optional<PixelType> pixelType = ApisHandler.getPixelType(meta.get("pixelsType").getAsString());

                if (pixelType.isPresent()) {
                    metadataBuilder.pixelType(pixelType.get());
                } else {
                    logger.error(String.format("Cannot convert %s to a known pixel type", meta.get("pixelsType").getAsString()));
                }
                return pixelType;
            } else {
                logger.error(String.format("'pixelsType' string not found in %s", meta));
                return Optional.empty();
            }
        } else {
            logger.debug(String.format("'meta' JSON object not found in %s", metadataResponse));
            return Optional.empty();
        }
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
            logger.debug(String.format("'tile_size' JSON object not found in %s", metadataResponse));
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
                logger.debug(String.format("'zoomLevelScaling' JSON object not found in %s", metadataResponse));
            }
        }
    }

    private static Optional<Number> getNumberFromJsonObject(JsonObject jsonObject, String attributeName, LoggingEventBuilder logger) {
        if (jsonObject.has(attributeName) && jsonObject.get(attributeName).isJsonPrimitive() && jsonObject.getAsJsonPrimitive(attributeName).isNumber()) {
            return Optional.of(jsonObject.getAsJsonPrimitive(attributeName).getAsNumber());
        } else {
            logger.log(String.format("'%s' number not found in %s", attributeName, jsonObject));
            return Optional.empty();
        }
    }
}
