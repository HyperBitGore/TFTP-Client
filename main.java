public class main {
    public static void main(String[] args) {
        System.out.println("Starting the TFTP client...");
        TFTP tftp = new TFTP("localhost", 69);
        tftp.readFile("example.txt", "example_received.txt");
    }
}