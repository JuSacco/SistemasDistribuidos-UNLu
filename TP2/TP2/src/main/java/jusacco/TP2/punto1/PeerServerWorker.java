package jusacco.TP2.punto1;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PeerServerWorker implements Runnable {
	ArrayList<Archivo> liArchivos;
	Socket client;
	String directory;
	BufferedReader inputChannel;
	PrintWriter outputChannel;
	private final Logger log = LoggerFactory.getLogger(Config.class);
	
	public PeerServerWorker(ArrayList<Archivo> liArchivos, Socket client,String directory) {
		this.client = client;
		this.liArchivos = liArchivos;
		this.directory = directory;
	}
	
	private byte[] readFileToByteArray(File file){
        FileInputStream fis = null;
        byte[] bArray = new byte[(int) file.length()];
        try{
            fis = new FileInputStream(file);
            fis.read(bArray);
            fis.close();        
        }catch(IOException e){
            e.printStackTrace();
        }
        return bArray;
    }
	
	public void enviarArchivo(String name) {
        try {
			Archivo toSend = null;
			File file;
			int index = lookupArchivo(name);
			if (index >= 0) {
				file = new File(directory+"/"+name);
				if (file.exists()) {
					byte[] bArray = readFileToByteArray(file);
					toSend = new Archivo(name,bArray);		
			        outputChannel.println("sending");
			        outputChannel.flush();
					ObjectOutputStream os = new ObjectOutputStream(this.client.getOutputStream());
					log.info("Worker enviando el archivo: "+name);	
					os.writeObject(toSend);	       
				}else {
			        outputChannel.println("error");
			        log.error("Archivo "+name+" no existe");
				}
			}else {
				outputChannel.println("error");
			}
			} catch (IOException e) {
				e.printStackTrace();
			}
	}
	
	public int lookupArchivo(String name) {
		for (Archivo archivo : liArchivos) {
			if(archivo.getName().contains(name)) {
				return liArchivos.indexOf(archivo);
			}
		}
		return -1;
	}
	
	public ArrayList<String> lookupMultipleArchivo(String name) {
		ArrayList<String> resultado = null;
		int counter = 0;
		for (Archivo archivo : liArchivos) {
			counter ++;
			if(archivo.getName().contains(name)) {
				resultado.add(counter+"."+archivo.getName());
			}
		}
		return resultado;
	}
	
	
	
	@Override
	public void run() {
		String msg;
		String[] msgParced;
		try {
			this.inputChannel = new BufferedReader (new InputStreamReader (this.client.getInputStream()));
			this.outputChannel = new PrintWriter (this.client.getOutputStream(), true);
			msg = this.inputChannel.readLine();
			System.out.println("MSG: "+msg);
			msgParced = msg.split("=");
			switch (msgParced[0]) {
			case "buscar":
				ArrayList<String> busqueda = lookupMultipleArchivo(msgParced[1]);
				if (!(busqueda == null)) {
					for(String m : busqueda)
						this.outputChannel.println(m);
				}
				break;
			case "descargar":
				enviarArchivo(msgParced[1]);
				log.info("Archivo enviado!");
				break;
			default:
				break;
			}
			this.outputChannel.println(msg);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
