package qupath.ext.omero.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.TestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TestArgsUtils {

    @Test
    void Check_No_Argument_Found_In_Empty_List() {
        String label = "some_label";
        List<String> args = List.of();

        Optional<String> arg = ArgsUtils.findArgInList(label, args);

        Assertions.assertTrue(arg.isEmpty());
    }

    @Test
    void Check_No_Argument_Found_In_List() {
        String label = "some_label";
        List<String> args = List.of("a", "b", "c");

        Optional<String> arg = ArgsUtils.findArgInList(label, args);

        Assertions.assertTrue(arg.isEmpty());
    }

    @Test
    void Check_No_Argument_Found_When_Label_Is_Last_Element() {
        String label = "some_label";
        List<String> args = List.of("a", "b", label);

        Optional<String> arg = ArgsUtils.findArgInList(label, args);

        Assertions.assertTrue(arg.isEmpty());
    }

    @Test
    void Check_Argument_Found_In_List() {
        String label = "some_label";
        String expectedArg = "some_value";
        List<String> args = List.of(label, expectedArg);

        String arg = ArgsUtils.findArgInList(label, args).orElse(null);

        Assertions.assertEquals(expectedArg, arg);
    }

    @Test
    void Check_Argument_Found_And_Trimmed_In_List() {
        String label = "some_label";
        String expectedArg = "some_value";
        List<String> args = List.of("   some_label   ", "   some_value  ");

        String arg = ArgsUtils.findArgInList(label, args).orElse(null);

        Assertions.assertEquals(expectedArg, arg);
    }

    @Test
    void Check_Argument_Found_In_List_With_Other_Args() {
        String label = "some_label";
        String expectedArg = "some_value";
        List<String> args = List.of("a", label, expectedArg, "b", "c");

        String arg = ArgsUtils.findArgInList(label, args).orElse(null);

        Assertions.assertEquals(expectedArg, arg);
    }

    @Test
    void Check_Replaced_Args_In_Empty_List_And_Map() {
        List<String> args = List.of();
        Map<String, String> labelsToValues = Map.of();
        List<String> expectedReplacedArgs = List.of();

        List<String> replacedArgs = ArgsUtils.replaceArgs(args, labelsToValues);

        TestUtils.assertCollectionsEqualsWithoutOrder(expectedReplacedArgs, replacedArgs);
    }

    @Test
    void Check_Replaced_Args_In_Empty_List() {
        List<String> args = List.of();
        Map<String, String> labelsToValues = Map.of("some_label", "some_new_value");
        List<String> expectedReplacedArgs = List.of("some_label", "some_new_value");

        List<String> replacedArgs = ArgsUtils.replaceArgs(args, labelsToValues);

        TestUtils.assertCollectionsEqualsWithoutOrder(expectedReplacedArgs, replacedArgs);
    }

    @Test
    void Check_Replaced_Args_In_Empty_Map() {
        List<String> args = List.of("some_label", "some_value");
        Map<String, String> labelsToValues = Map.of();

        List<String> replacedArgs = ArgsUtils.replaceArgs(args, labelsToValues);

        TestUtils.assertCollectionsEqualsWithoutOrder(args, replacedArgs);
    }

    @Test
    void Check_Replaced_Args() {
        List<String> args = List.of("some_label", "some_value");
        Map<String, String> labelsToValues = Map.of("some_label", "some_new_value");
        List<String> expectedReplacedArgs = List.of("some_label", "some_new_value");

        List<String> replacedArgs = ArgsUtils.replaceArgs(args, labelsToValues);

        TestUtils.assertCollectionsEqualsWithoutOrder(expectedReplacedArgs, replacedArgs);
    }

    @Test
    void Check_Filtered_Args() {
        List<String> args = List.of("some_label", "some_value");
        Map<String, String> labelsToValues = new HashMap<>();
        labelsToValues.put("some_label", null);
        List<String> expectedReplacedArgs = List.of();

        List<String> replacedArgs = ArgsUtils.replaceArgs(args, labelsToValues);

        TestUtils.assertCollectionsEqualsWithoutOrder(expectedReplacedArgs, replacedArgs);
    }

    @Test
    void Check_Replaced_Args_With_Spaces() {
        List<String> args = List.of("   some_label  ", "some_value");
        Map<String, String> labelsToValues = Map.of("some_label", "some_new_value");
        List<String> expectedReplacedArgs = List.of("some_label", "some_new_value");

        List<String> replacedArgs = ArgsUtils.replaceArgs(args, labelsToValues);

        TestUtils.assertCollectionsEqualsWithoutOrder(expectedReplacedArgs, replacedArgs);
    }

    @Test
    void Check_Replaced_Args_When_List_Has_Other_Args() {
        List<String> args = List.of("a", "some_label", "some_value", "b", "c");
        Map<String, String> labelsToValues = Map.of("some_label", "some_new_value");
        List<String> expectedReplacedArgs = List.of("a", "some_label", "some_new_value", "b", "c");

        List<String> replacedArgs = ArgsUtils.replaceArgs(args, labelsToValues);

        TestUtils.assertCollectionsEqualsWithoutOrder(expectedReplacedArgs, replacedArgs);
    }

    @Test
    void Check_Replaced_Args_When_Multiple_Labels() {
        List<String> args = List.of("a", "some_label1", "some_value1", "b", "c", "some_label2", "some_value2");
        Map<String, String> labelsToValues = Map.of(
                "some_label1", "some_new_value1",
                "some_label2", "some_new_value2"
        );
        List<String> expectedReplacedArgs = List.of("a", "some_label1", "some_new_value1", "b", "c", "some_label2", "some_new_value2");

        List<String> replacedArgs = ArgsUtils.replaceArgs(args, labelsToValues);

        TestUtils.assertCollectionsEqualsWithoutOrder(expectedReplacedArgs, replacedArgs);
    }

    @Test
    void Check_Replaced_Args_When_Label_At_The_End() {
        List<String> args = List.of("a", "some_label");
        Map<String, String> labelsToValues = Map.of("some_label", "some_new_value");
        List<String> expectedReplacedArgs = List.of("a", "some_label", "some_new_value");

        List<String> replacedArgs = ArgsUtils.replaceArgs(args, labelsToValues);

        TestUtils.assertCollectionsEqualsWithoutOrder(expectedReplacedArgs, replacedArgs);
    }

    @Test
    void Check_Replaced_Args_When_Label_Not_Present() {
        List<String> args = List.of("a");
        Map<String, String> labelsToValues = Map.of("some_label", "some_new_value");
        List<String> expectedReplacedArgs = List.of("a", "some_label", "some_new_value");

        List<String> replacedArgs = ArgsUtils.replaceArgs(args, labelsToValues);

        TestUtils.assertCollectionsEqualsWithoutOrder(expectedReplacedArgs, replacedArgs);
    }
}
