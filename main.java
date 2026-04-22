public class main {
    public static void main(String[] args) {
        System.out.println("Starting the TFTP client...");
        System.out.println("Please input the server address: ");
        String serverAddress = System.console().readLine();
        serverAddress = serverAddress.trim();
        boolean exit = false;
        String mode = "netascii";
        while (!exit) {
            System.out.println("Enter 'r' to read a file, 'w' to write a file, 'o' to set mode, or 'q' to quit:");
            String command = System.console().readLine();
            // easier than trying to manage a single TFTP instance across commands
            TFTP tftp = new TFTP(serverAddress, 69);
            if (command.equalsIgnoreCase("r")) {
                System.out.println("Enter the filename to read from the server:");
                String filename = System.console().readLine();
                filename = filename.trim();
                System.out.println("Enter the output filename to save the received file:");
                String outputFilename = System.console().readLine();
                outputFilename = outputFilename.trim();
                tftp.readFile(filename, outputFilename, mode);
            } else if (command.equalsIgnoreCase("w")) {
                System.out.println("Enter the filename to write to the server:");
                String filename = System.console().readLine();
                filename = filename.trim();
                tftp.writeFile(filename, mode);
            } else if (command.equalsIgnoreCase("q")) {
                exit = true;
            } else if (command.equalsIgnoreCase("o")) {
                System.out.println("Enter the mode ('netascii' or 'octet'):");
                mode = System.console().readLine();
                mode = mode.trim();
                if (!mode.equalsIgnoreCase("netascii") && !mode.equalsIgnoreCase("octet")) {
                    System.out.println("Invalid mode. Defaulting to 'netascii'.");
                    mode = "netascii";
                }
            } else {
                System.out.println("Invalid command. Please enter 'r', 'w', 'o', or 'q'.");
            }
        }
    }
}