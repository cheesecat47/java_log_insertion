package kr.ac.knu.isslab;

class TestInvoke {
    private static int calls=0;
    public static void main(String[] args) {

        for (int i=0; i<10; i++) {
            foo();
        }

        System.out.println(ha());

        System.out.println("I made "+calls+" static calls");
    }

    private static void foo(){
        calls++;
        System.out.println(bar());
    }

    private static String bar(){
        calls++;
        return "bar";
    }

    private static String ha() {
        calls++;
        return "Ha!";
    }
}