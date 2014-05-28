import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Vector;

import javax.swing.JFrame;
import javax.swing.JOptionPane;

/**
 * Multithreaded chat xbeeServer
 * Clients connect on port 4242 and give their name
 * Then the xbeeServer accepts input and broadcasts it to the other clients
 * Connect by running Client.java
 * 
 * @author Syrus
 */

public class XbeeServer {
	private int id;
	private ServerSocket listen;								// for accepting connections
	private ArrayList<ClientHandler> handlers;					// all the connections with clients

	DatabaseConnect db;

	public XbeeServer(int id, ServerSocket listen, String database, String table) {
		this.id=id;
		this.listen = listen;
		handlers = new ArrayList<ClientHandler>();
		try {
			db=new DatabaseConnect(database,table);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	/**
	 * The usual loop of accepting connections and firing off new threads to handle them
	 */
	public void getConnections() throws IOException {
		while (true) {
			Socket sc=listen.accept();
			sc.getInputStream();
			ClientHandler handler = new ClientHandler(listen.accept(), this);
			handler.setDaemon(true);
			addHandler(handler);
			handler.start();
		}
	}

	/**
	 * Adds the handler to the list of current client handlers
	 */
	public synchronized void addHandler(ClientHandler handler) {
		handlers.add(handler);
	}

	/**
	 * Removes the handler from the list of current client handlers
	 */
	public synchronized void removeHandler(ClientHandler handler) {
		handlers.remove(handler);
	}

	/**
	 * Sends the message from the one client handler to all the others (but not echoing back to the originator)
	 */
	public synchronized void broadcast(ClientHandler from, String msg) {
		for (ClientHandler h : handlers) {
			if (h != from) {
				h.out.println(msg);
			}
		}
	}

	/**
	 * Insert or Update item in Database
	 */
	public synchronized void insertItem(ClientHandler from, XbeeData v){
		boolean found=false;
		Integer data[] = v.analogData;
		if(data==null || data.length==0) return;
		for(int i=0; i<from.Nodes.size(); i++)
			if(from.Nodes.elementAt(i).getId()==v.id){
				found=true;
				from.Nodes.elementAt(i).updateValue(data);
				if(from.Nodes.elementAt(i).getCount()==0) {
					db.insertItem(from.Nodes.elementAt(i));
					try(PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(""+from.Nodes.elementAt(i).getId(), true)))) {
						data = from.Nodes.elementAt(i).getValue();
						for(int j=0; j<data.length; j++)
							out.print(data[j]+",");
					}catch (IOException e) {
						//exception handling left as an exercise for the reader
					}
					break;
				}
			}
		if(!found){
			JFrame frame = new JFrame("This is it");
			frame.setVisible(true);
			String str=JOptionPane.showInputDialog(frame, "Coordinate for Node"+(from.Nodes.size()+1), "Location", JOptionPane.QUESTION_MESSAGE);
			String tok[] = str.split("[,]");
			int x=Integer.parseInt(tok[0]);
			int y=Integer.parseInt(tok[1]);
			Item it=new Item(v.id, data, x+","+y);
			from.Nodes.addElement(it);
		}
	}

	/**
	 * Handles communication between a xbeeServer and one client
	 */
	public class ClientHandler extends Thread {
		private Socket sock;					// each instance is in a different thread and has its own socket
		private XbeeServer xbeeServer;				// the main xbeeServer instance
		private PrintWriter out;
		public Vector<Item> Nodes;

		public ClientHandler(Socket sock, XbeeServer xbeeServer) {
			super("ClientHandler");
			this.sock = sock;
			this.xbeeServer = xbeeServer;
			Nodes = new Vector<Item>();
		}

		public void run() {
			try {
				System.out.println("someone connected");

				// Communication channel
				out = new PrintWriter(sock.getOutputStream(), true);
				BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
				out.write("Who?");
				out.flush();
				int id=in.read();
				if(id<0){
					;
				}
				else{
					while(sock.isConnected()){
						XbeeData v=Library.test(in);
						System.out.println("ID: "+v.id);

						System.out.println("Digital Data :");
						for(int j=0; j<v.digitalData.length; j++)
							System.out.print(v.digitalData[j]+" ");
						System.out.println();

						System.out.println("Analog Data :");
						for(int j=0; j<v.analogData.length; j++)
							System.out.print(v.analogData[j]+" ");
						System.out.println();

						xbeeServer.insertItem(this,v);
					}
				}
				// Done
				System.out.println(" hung up");

				// Clean up -- note that also remove self from xbeeServer's list of handlers so it doesn't broadcast here
				xbeeServer.removeHandler(this);
				out.close();
				in.close();
				sock.close();
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static void main(String[] args) throws Exception {
		System.out.println("waiting for connections");
		new XbeeServer(1, new ServerSocket(4242), "xbee", "flashflood").getConnections();
	}
}