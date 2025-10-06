package qupath.ext.omero.core.apis;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.core.apis.json.Utils;

import java.nio.charset.StandardCharsets;

public class TestUtils {

    @Test
    void Check_Char_Concat_And_Converted_To_Bytes() {
        char[] firstPart = "Lorem ipsum dolor sit amet, consectetur adipiscing elit,".toCharArray();
        char[] secondPart = " sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.".toCharArray();
        byte[] expected = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua."
                .getBytes(StandardCharsets.UTF_8);

        byte[] concatenation = Utils.concatAndConvertToBytes(firstPart, secondPart);

        Assertions.assertArrayEquals(expected, concatenation);
    }

    @Test
    void Check_Input_Of_Char_Concat_And_Converted_To_Bytes_Is_Cleared() {
        char[] firstPart = "Lorem ipsum dolor sit amet, consectetur adipiscing elit,".toCharArray();
        char[] secondPart = " sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.".toCharArray();
        char[] expectedFirstPart = new char[firstPart.length];
        char[] expectedSecondPart = new char[secondPart.length];

        Utils.concatAndConvertToBytes(firstPart, secondPart);

        Assertions.assertArrayEquals(expectedFirstPart, firstPart);
        Assertions.assertArrayEquals(expectedSecondPart, secondPart);
    }

    @Test
    void Check_Text_URL_Encoded() {
        char[] text = "!#$%&'()+,/0123456789".toCharArray();
        char[] expectedEncodedText = "%21%23%24%25%26%27%28%29%2B%2C%2F0123456789".toCharArray();

        char[] encodedText = Utils.urlEncode(text);

        Assertions.assertArrayEquals(expectedEncodedText, encodedText);
    }

    @Test
    void Check_Input_Of_Text_URL_Encoded_Is_Cleared() {
        char[] text = "!#$%&'()+,/0123456789".toCharArray();
        char[] expectedText = new char[text.length];

        Utils.urlEncode(text);

        Assertions.assertArrayEquals(expectedText, text);
    }
}
