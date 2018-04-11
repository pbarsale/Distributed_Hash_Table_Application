package edu.buffalo.cse.cse486586.simpledht;

/**
 * Created by prati on 4/5/2018.
 */

public class Node {

    private String remoteport;
    private String hash;
    private String predecessor;
    private String successor;
    private String predecessor_port;
    private String successor_port;

    public String getPredecessor_port() {
        return predecessor_port;
    }

    public void setPredecessor_port(String predecessor_port) {
        this.predecessor_port = predecessor_port;
    }

    public String getSuccessor_port() {
        return successor_port;
    }

    public void setSuccessor_port(String successor_port) {
        this.successor_port = successor_port;
    }

    public Node(String remoteport, String hash, String predecessor, String successor, String predecessor_port, String successor_port) {
        this.remoteport = remoteport;
        this.hash = hash;
        this.predecessor = predecessor;
        this.successor = successor;
        this.predecessor_port = predecessor_port;
        this.successor_port = successor_port;
    }

    public String getPredecessor() {
        return predecessor;
    }

    public void setPredecessor(String predecessor) {
        this.predecessor = predecessor;
    }

    public String getRemoteport() {
        return remoteport;
    }

    public void setRemoteport(String remoteport) {
        this.remoteport = remoteport;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getSuccessor() {
        return successor;
    }

    public void setSuccessor(String successor) {
        this.successor = successor;
    }
}
