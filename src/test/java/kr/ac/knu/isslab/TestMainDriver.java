package kr.ac.knu.isslab;

import org.junit.jupiter.api.Test;

import java.io.IOException;

public class TestMainDriver {
    @Test
    public void testMain() throws IOException {
        MainDriver.main(new String[]{
                "-cp", "/Users/refo/Developer/asm_ex/soot_method_name/target/classes:/Users/refo/Developer/asm_ex/soot_method_name/target/test-classes",
                "kr.ac.knu.isslab.MainDriver", "kr.ac.knu.isslab.TestInvoke"});
    }
}
