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
                // in output data
                for (int i = 0; i < outputData.length; i++) {
                    data[3 + filename.length() + i] = outputData[i];
                }
                data[3 + filename.length() + outputData.length] = 0; // Null-terminate the mode
                data_length = 3 + filename.length() + outputData.length + 1;
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
                // in output data
                for (int i = 0; i < outputData.length; i++) {
                    data[3 + filename.length() + i] = outputData[i];
                }
                data[3 + filename.length() + outputData.length] = 0; // Null-terminate the mode
                data_length = 3 + filename.length() + outputData.length + 1;
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
        int port = packet.getPort();
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

    public void writeFile(String filename, String mode) {
        socket = null;
        try {
            socket = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
        }
        sendPacket(PacketType.WRQ, filename, mode.getBytes(), mode.getBytes().length);
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
        byte[] netascii_buffer = new byte[2048]; // buffer of leftover bytes when we convert to netascii carriage returns
        int netascii_index = 0;
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
            if (mode.equalsIgnoreCase("netascii")) {
                // need to convert output_buffer from normal to netascii
                // write data into netascii queue, converting line endings as needed
                for (int j = 0; j < read && j < buffer.length && netascii_index < netascii_buffer.length; j++) {
                    if (buffer[j] == '\r' && j + 1 < read && buffer[j + 1] == '\n') {
                        netascii_buffer[netascii_index++] = '\r';
                        netascii_buffer[netascii_index++] = '\n';
                        j++; // skip the next character as we have already processed it
                    } else if (buffer[j] == '\n' && (netascii_index == 0 || netascii_buffer[netascii_index - 1] != '\r')) {
                        netascii_buffer[netascii_index++] = '\r';
                        netascii_buffer[netascii_index++] = '\n';
                    } else {
                        if (buffer[j] == '\r') {
                            byte[] nextByte = new byte[1];
                            try {
                                fis.read(nextByte);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            if (nextByte[0] != '\n') {
                                netascii_buffer[netascii_index++] = buffer[j];
                                netascii_buffer[netascii_index++] = nextByte[0];
                            } else {
                                netascii_buffer[netascii_index++] = '\r';
                                netascii_buffer[netascii_index++] = '\n';
                            }

                        } else {
                            netascii_buffer[netascii_index++] = buffer[j];
                        }
                    }

                }
                // take 512 bytes from netascii_buffer and send as DATA packet
                int bytes_to_send = Math.min(512, netascii_index);
                System.arraycopy(netascii_buffer, 0, output_buffer, 2, bytes_to_send);
                sendPacket(PacketType.DATA, null, output_buffer, bytes_to_send + 2);
                // shift any remaining bytes in netascii_buffer to the beginning
                if (netascii_index > 512) {
                    int remaining = netascii_index - 512;
                    System.arraycopy(netascii_buffer, 512, netascii_buffer, 0, remaining);
                    netascii_index = remaining;
                } else {
                    netascii_index = 0;
                }

            } else {
                sendPacket(PacketType.DATA, null, output_buffer, read + 2);
            }
            block_number++;
            ReceivedPacket ackPacket = receivePacket();
            if (ackPacket.type == PacketType.ACK) {
                System.out.println("Received ACK for block " + (block_number - 1) + " from port " + ackPacket.port);
            } else if (ackPacket.type == PacketType.ERROR) {
                System.out.println("Received ERROR packet while waiting for ACK with message: " + new String(ackPacket.data, 4, ackPacket.length - 4));
                return;
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
    public void readFile(String filename, String output_filename, String mode) {
        socket = null;
        try {
            socket = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
        }
        sendPacket(PacketType.RRQ, filename, mode.getBytes(), mode.getBytes().length);
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
                try {
                    if (mode.equalsIgnoreCase("netascii")) {
                        for (int i = 4; i < receivedPacket.length; i++) {
                            if (receivedPacket.data[i] == '\r') {
                                if (i + 1 < receivedPacket.length && receivedPacket.data[i + 1] == '\0') {
                                    fos.write('\r');
                                    i++;
                                } else if (i + 1 < receivedPacket.length && receivedPacket.data[i + 1] == '\n') {
                                    fos.write('\r');
                                    fos.write('\n');
                                    i++;
                                } else {
                                    fos.write('\r');
                                }
                            } else {
                                fos.write(receivedPacket.data[i]);
                            }
                        }
                    } else {
                        fos.write(receivedPacket.data, 4, receivedPacket.length - 4);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                // Check if this is the last DATA packet (length < 512)
                if (receivedPacket.length < 516) {
                    terminate = true;
                }
            } else if (receivedPacket.type == PacketType.ERROR) {
                System.out.println("Received ERROR packet while waiting for DATA with message: " + new String(receivedPacket.data, 4, receivedPacket.length - 4));
                return;
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
