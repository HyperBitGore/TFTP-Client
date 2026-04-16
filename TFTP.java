import java.io.File;
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

        public ReceivedPacket(byte[] data, int length, PacketType type) {
            this.data = data;
            this.length = length;
            this.type = type;
        }
    }

    private String serverAddress;
    private int serverPort;
    private int tid;
    private DatagramSocket socket = null;

    public TFTP(String serverAddress, int serverPort) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.tid = 0;
    }

    private void pickTID() {
        this.tid = (int) (Math.random() * 65535);
    }

    private void sendPacket(PacketType type, String filename, byte[] outputData) {
        byte[] data = new byte[512];
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
                break;
            case ERROR:
                // Construct an Error packet
                data[0] = 0;
                data[1] = (byte) PacketType.ERROR.code;
                break;
            case ACK:
                data[0] = 0;
                data[1] = (byte) PacketType.ACK.code;
                // need block number
                for (int i = 0; i < 2; i++) {
                    data[2 + i] = outputData[i];
                }
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
        // Code to receive and handle packets goes here
        DatagramPacket packet = new DatagramPacket(new byte[512], 512);
        try {
            socket.receive(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
        byte[] data = packet.getData();
        // opcode is in first two bytes
        int opcode = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
        PacketType type = null;
        for (PacketType pt : PacketType.values()) {
            if (pt.code == opcode) {
                type = pt;
                break;
            }
        }
        ReceivedPacket receivedPacket = new ReceivedPacket(data, packet.getLength(), type);
        return receivedPacket;
    }

    public void writeFile(String filename) {
        pickTID();
        socket = null;
        try {
            socket = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
        }
        sendPacket(PacketType.WRQ, filename, null);
        ReceivedPacket receivedPacket = receivePacket();
        if (receivedPacket.type == PacketType.ACK) {
            System.out.println("Received ACK for WRQ");
        } else {
            System.out.println("Unexpected packet type: " + receivedPacket.type);
            return;
        }
        boolean terminate = false;
        while (!terminate) {
            
        }
        socket.close();
    }
    public void readFile(String filename, String output_filename) {
        pickTID();
        socket = null;
        try {
            socket = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
        }
        sendPacket(PacketType.RRQ, filename, null);
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
                System.out.println("Received DATA packet with length: " + receivedPacket.length);
                // Send ACK for the received DATA packet
                byte[] blockNumber = new byte[2];
                blockNumber[0] = receivedPacket.data[2];
                blockNumber[1] = receivedPacket.data[3];
                sendPacket(PacketType.ACK, null, blockNumber);
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
