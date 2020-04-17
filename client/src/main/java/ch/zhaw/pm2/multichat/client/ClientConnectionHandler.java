package ch.zhaw.pm2.multichat.client;

import ch.zhaw.pm2.multichat.protocol.ChatProtocolException;
import ch.zhaw.pm2.multichat.protocol.ConnectionHandler;
import ch.zhaw.pm2.multichat.protocol.NetworkHandler;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;

import static ch.zhaw.pm2.multichat.client.ClientConnectionHandler.State.*;

public class ClientConnectionHandler extends ConnectionHandler {
    private final ChatWindowController controller;
    public static final String USER_ALL = "*"; //TODO: ??
    private State state = NEW;

    enum State {
        NEW, CONFIRM_CONNECT, CONNECTED, CONFIRM_DISCONNECT, DISCONNECTED;
    }

    public ClientConnectionHandler(NetworkHandler.NetworkConnection<String> connection,
                                   String userName,
                                   ChatWindowController controller)  {
        super(connection);
        this.userName = (userName == null || userName.isBlank())? USER_NONE : userName;
        this.controller = controller;
    }

    public State getState() {
        return this.state;
    }

    public void setState (State newState) {
        this.state = newState;
        controller.stateChanged(newState);
    }

    public void startReceiving() {
        logger.info("Starting Connection Handler");
        try {
            logger.info("Start receiving data...");
            while (connection.isAvailable()) {
                String data = connection.receive();
                processData(data);
            }
            logger.info("Stopped recieving data");
        } catch (SocketException e) {
            logger.info("Connection terminated locally");
            this.setState(DISCONNECTED);
            logger.warning("Unregistered because connection terminated" + e.getMessage());
        } catch (EOFException e) {
            logger.info("Connection terminated by remote");
            this.setState(DISCONNECTED);
            logger.warning("Unregistered because connection terminated" + e.getMessage());
        } catch(IOException e) {
            logger.warning("Communication error" + e);
        } catch(ClassNotFoundException e) {
            logger.warning("Received object of unknown type" + e.getMessage());
        }
        logger.info("Stopped Connection Handler");
    }

    public void stopReceiving() {
        logger.info("Closing Connection Handler to Server");
        try {
            logger.info("Stop receiving data...");
            connection.close();
            logger.info("Stopped receiving data.");
        } catch (IOException e) {
            logger.warning("Failed to close connection." + e.getMessage());
        }
        logger.info("Closed Connection Handler to Server");
    }

    private void processData(String data) {
        parseData(data);
        // dispatch operation based on type parameter
        if (type.equals(DATA_TYPE_CONNECT)) {
            logger.warning("Illegal connect request from server");
        } else if (type.equals(DATA_TYPE_CONFIRM)) {
            if (state == CONFIRM_CONNECT) {
                this.userName = reciever;
                controller.setUserName(userName);
                controller.setServerPort(connection.getRemotePort());
                controller.setServerAddress(connection.getRemoteHost());
                controller.writeInfo(payload);
                logger.info("CONFIRM: " + payload);
                this.setState(CONNECTED);
            } else if (state == CONFIRM_DISCONNECT) {
                controller.writeInfo(payload);
                logger.info("CONFIRM: " + payload);
                this.setState(DISCONNECTED);
            } else {
                logger.warning("Got unexpected confirm message: " + payload);
            }
        } else if (type.equals(DATA_TYPE_DISCONNECT)) {
            if (state == DISCONNECTED) {
                logger.info("DISCONNECT: Already in disconnected: " + payload);
                return;
            }
            controller.writeInfo(payload);
            logger.info("DISCONNECT: " + payload);
            this.setState(DISCONNECTED);
        } else if (type.equals(DATA_TYPE_MESSAGE)) {
            if (state != CONNECTED) {
                logger.info("MESSAGE: Illegal state " + state + " for message: " + payload);
                return;
            }
            controller.writeMessage(sender, reciever, payload);
            logger.severe("MESSAGE: From " + sender + " to " + reciever + ": " + payload);
        } else if (type.equals(DATA_TYPE_ERROR)) {
            controller.writeError(payload);
            logger.severe("ERROR: " + payload);
        } else {
            logger.severe("Unknown data type received: " + type);
        }
    }

    public void connect() throws ChatProtocolException {
        if (state != NEW) throw new ChatProtocolException("Illegal state for connect: " + state);
        this.sendData(userName, USER_NONE, DATA_TYPE_CONNECT,null);
        this.setState(CONFIRM_CONNECT);
    }

    public void disconnect() throws ChatProtocolException {
        if (state != NEW && state != CONNECTED) throw new ChatProtocolException("Illegal state for disconnect: " + state);
        this.sendData(userName, USER_NONE, DATA_TYPE_DISCONNECT,null);
        this.setState(CONFIRM_DISCONNECT);
    }

    public void message(String receiver, String message) throws ChatProtocolException {
        if (state != CONNECTED) throw new ChatProtocolException("Illegal state for message: " + state);
        this.sendData(userName, receiver, DATA_TYPE_MESSAGE,message);
    }

}
