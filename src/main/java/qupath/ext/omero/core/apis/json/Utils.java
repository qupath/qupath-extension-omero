package qupath.ext.omero.core.apis.json;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Utility methods used by the JSON API.
 */
class Utils {

    private Utils() {
        throw new AssertionError("This class is not instantiable.");
    }

    /**
     * <p>Concatenate and convert two char arrays to a byte array using the UTF 8 encoding.</p>
     * <p>The input parameters are cleared (filled with zeros) once processed.</p>
     *
     * @param arr1 the array that will be concatenated on the left
     * @param arr2 the array that will be concatenated on the right
     * @return the concatenation of the two arrays in the byte format
     */
    public static byte[] concatAndConvertToBytes(char[] arr1, char[] arr2) {
        return toBytes(concatChars(arr1, arr2));
    }

    /**
     * <p>
     *     Converts a char array to the application/x-www-form-urlencoded MIME format
     *     (see the <a href="http://www.w3.org/TR/html4/">HTML specifications</a>).
     * </p>
     * <p>The input array is cleared (filled with zeros) once processed.</p>
     *
     * @param text the array to convert
     * @return the encoded char array
     */
    public static char[] urlEncode(char[] text) {
        char[] encodedText = CharArrayURLEncoder.encode(text, StandardCharsets.UTF_8);

        // Clear sensitive data
        Arrays.fill(text, (char) 0);

        return encodedText;
    }

    private static byte[] toBytes(char[] chars) {
        CharBuffer charBuffer = CharBuffer.wrap(chars);
        ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(charBuffer);

        byte[] bytes = Arrays.copyOfRange(byteBuffer.array(), byteBuffer.position(), byteBuffer.limit());

        // Clear sensitive data
        Arrays.fill(byteBuffer.array(), (byte) 0);

        return bytes;
    }

    private static char[] concatChars(char[] arr1, char[] arr2) {
        char[] result = new char[arr1.length + arr2.length];

        System.arraycopy(arr1, 0, result, 0, arr1.length);
        System.arraycopy(arr2, 0, result, arr1.length, arr2.length);

        // Clear sensitive data
        Arrays.fill(arr1, (char) 0);
        Arrays.fill(arr2, (char) 0);

        return result;
    }
}
