import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class TFTP {
    enum PacketType {
        RRQ(1),
        WRQ(2),
        DATA(3),
        ACK(4),
        ERROR(5);
        public final int code;
        private PacketType(int code) {
            this.code = code;
        }
    }
    class ReceivedPacket {
        public byte[] data;
        public int length;
        public PacketType type;
        public int port;

        public ReceivedPacket(byte[] data, int length, PacketType type, int port) {
            this.data = data;
            this.length = length;
            this.type = type;
            this.port = port;
        }
    }

    private String serverAddress;
    private int serverPort; // the TID in the RFC
    private DatagramSocket socket = null;

    public TFTP(String serverAddress, int serverPort) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
    }

    private void sendPacket(PacketType type, String filename, byte[] outputData, int length) {
        byte[] data = new byte[516];
        int data_length = 0;
        // Construct and send a TFTP packet based on the type and filename
        switch (type) {
            case RRQ:
                // Construct a Read Request packet
                data[0] = 0;
                data[1] = (byte) PacketType.RRQ.code;
                for (int i = 0; i < filename.length(); i++) {
                    data[2 + i] = (byte) filename.charAt(i);
                }
                data[2 + filename.length()] = 0; // Null-terminate the filename
                // mode
                for (int i = 0; i < "netascii".length(); i++) {
                    data[3 + filename.length() + i] = (byte) "netascii".charAt(i);
                }
                data[3 + filename.length() + "netascii".length()] = 0; // Null-terminate the mode
                data_length = 3 + filename.length() + "netascii".length() + 1;
                break;
            case WRQ:
                // Construct a Write Request packet
                data[0] = 0;
                data[1] = (byte) PacketType.WRQ.code;
                for (int i = 0; i < filename.length(); i++) {
                    data[2 + i] = (byte) filename.charAt(i);
                }
                data[2 + filename.length()] = 0; // Null-terminate the filename
                // mode
                for (int i = 0; i < "netascii".length(); i++) {
                    data[3 + filename.length() + i] = (byte) "netascii".charAt(i);
                }
                data[3 + filename.length() + "netascii".length()] = 0; // Null-terminate the mode
                data_length = 3 + filename.length() + "netascii".length() + 1;
                break;
            case DATA:
                // Construct a Data packet
                data[0] = 0;
                data[1] = (byte) PacketType.DATA.code;
                // need block number and data
                for (int i = 0; i < length; i++) {
                    data[2 + i] = outputData[i];
                }   
                data_length = 2 + length;
                break;
            case ERROR:
                // Construct an Error packet
                data[0] = 0;
                data[1] = (byte) PacketType.ERROR.code;
                // assuming we passed the error message as outputData
                for (int i = 0; i < outputData.length; i++) {
                    data[2 + i] = outputData[i];
                }
                data[2 + outputData.length] = 0;
                data_length = 2 + outputData.length + 1;
                break;
            case ACK:
                data[0] = 0;
                data[1] = (byte) PacketType.ACK.code;
                // block number
                data[2] = outputData[0];
                data[3] = outputData[1];
                data_length = 4;
                break;
            default:
                break;
        }
        InetAddress address = null;
        try {
            address = InetAddress.getByName(serverAddress);
        } catch (Exception e) {
            e.printStackTrace();
        }
        DatagramPacket packet = new DatagramPacket(data, data_length, address, serverPort);
        try {
            socket.send(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private ReceivedPacket receivePacket() {
        DatagramPacket packet = new DatagramPacket(new byte[516], 516);
        try {
            socket.receive(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
        byte[] data = packet.getData();
        int port =packet.getPort();
        // opcode is in first two bytes
        int opcode = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
        PacketType type = null;
        for (PacketType pt : PacketType.values()) {
            if (pt.code == opcode) {
                type = pt;
                break;
            }
        }
        if (type == null) {
            System.out.println("Received packet with unknown opcode: " + opcode);
            return null;
        }
        if (type == PacketType.ERROR) {
            int errorCode = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
            String errorMessage = new String(data, 4, packet.getLength() - 4);
            System.out.println("Received ERROR packet with code: " + errorCode + " and message: " + errorMessage);
        }
        ReceivedPacket receivedPacket = new ReceivedPacket(data, packet.getLength(), type, port);
        return receivedPacket;
    }

    public void writeFile(String filename) {
        socket = null;
        try {
            socket = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
        }
        sendPacket(PacketType.WRQ, filename, null, 0);
        ReceivedPacket receivedPacket = receivePacket();
        if (receivedPacket.type == PacketType.ACK) {
            System.out.println("Received ACK for WRQ from port " + receivedPacket.port);
        } else {
            System.out.println("Unexpected packet type: " + receivedPacket.type);
            return;
        }
        boolean terminate = false;
        int block_number = 1;
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(new File(filename));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        byte[] buffer = new byte[512];
        byte[] output_buffer = new byte[514];
        // need the correct server TID
        this.serverPort = receivedPacket.port; // Update server port to the one used by the server for this transfer
        while (!terminate) {
            int read = 0;
            try {
                read = fis.read(buffer);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (read == -1) {
                terminate = true;
                read = 0;
            } else if (read < 512) {
                terminate = true;
            }
            System.arraycopy(buffer, 0, output_buffer, 2, read);
            output_buffer[0] = (byte) (block_number >> 8);
            output_buffer[1] = (byte) (block_number & 0xFF);
            sendPacket(PacketType.DATA, null, output_buffer, read + 2);
            block_number++;
            ReceivedPacket ackPacket = receivePacket();
            if (ackPacket.type == PacketType.ACK) {
                System.out.println("Received ACK for block " + (block_number - 1) + " from port " + ackPacket.port);
            } else {
                System.out.println("Unexpected packet type: " + ackPacket.type);
                return;
            }
        }
        try {
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        socket.close();
    }
    public void readFile(String filename, String output_filename) {
        socket = null;
        try {
            socket = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
        }
        sendPacket(PacketType.RRQ, filename, null, 0);
        boolean terminate = false;
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(output_filename);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        while (!terminate) {
            ReceivedPacket receivedPacket = receivePacket();
            if (receivedPacket.type == PacketType.DATA) {
                serverPort = receivedPacket.port; // Update server port to the one used by the server for this transfer, TID
                System.out.println("Received DATA packet with length: " + receivedPacket.length + " from port " + receivedPacket.port);
                // Send ACK for the received DATA packet
                byte[] blockNumber = new byte[2];
                blockNumber[0] = receivedPacket.data[2];
                blockNumber[1] = receivedPacket.data[3];
                sendPacket(PacketType.ACK, null, blockNumber, 2);
                // Check if this is the last DATA packet (length < 512)
                try {
                    fos.write(receivedPacket.data, 4, receivedPacket.length - 4);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (receivedPacket.length < 516) {
                    terminate = true;
                }
            } else {
                System.out.println("Unexpected packet type: " + receivedPacket.type);
                return;
            }
        }
        try {
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        socket.close();
    }
}
