public class main {
    public static void main(String[] args) {
        System.out.println("Starting the TFTP client...");
        TFTP tftp = new TFTP("localhost", 69);
        boolean exit = false;
        // tftp.readFile("large.txt", "large_received.txt");
        while (!exit) {
            System.out.println("Enter 'r' to read a file, 'w' to write a file, or 'q' to quit:");
            String command = System.console().readLine();
            if (command.equalsIgnoreCase("r")) {
                System.out.println("Enter the filename to read from the server:");
                String filename = System.console().readLine();
                System.out.println("Enter the output filename to save the received file:");
                String outputFilename = System.console().readLine();
                tftp.readFile(filename, outputFilename);
            } else if (command.equalsIgnoreCase("w")) {
                System.out.println("Enter the filename to write to the server:");
                String filename = System.console().readLine();
                tftp.writeFile(filename);
            } else if (command.equalsIgnoreCase("q")) {
                exit = true;
            } else {
                System.out.println("Invalid command. Please enter 'r', 'w', or 'q'.");
            }
        }
        // tftp.writeFile("test_send.txt");
        // tftp.readFile("example.txt", "example_received.txt");
    }
}