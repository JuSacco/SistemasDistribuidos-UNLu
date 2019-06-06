package jusacco.TPFinal.Servidor;

import java.awt.image.BufferedImage;
import java.io.FileReader;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import jusacco.TPFinal.Cliente.Imagen;
import jusacco.TPFinal.Servidor.Tools.*;

public class Servidor implements IClient{
	//General settings
	Logger log = LoggerFactory.getLogger(Servidor.class);
	String myDirectory = System.getProperty("user.dir")+"\\ServerData\\";
	private int rmiPortCli;
	private int rmiPortSv;
	
	//Ftp Related
	String myFTPDirectory;
	private int ftpPort;
	ServerFtp ftp;
	
	//Queue Related
	private ConnectionFactory queueConnectionFactory;
	private Connection queueConnection;
	private Channel queueChannel;
	String queueUser;
	String queuePwd;
	String queueIp;
	private String queueTrabajo = "queueTrabajo";
	private String queueTerminados = "queueTerminados";
	
	private ArrayList<String> listaWorkers = new ArrayList<String>();
	private ArrayList<String> listaTrabajos = new ArrayList<String>();
	private String queuePort;
	
	public Servidor() {
		try {
			readConfigFile();
			initialConfig();
			runRMIServer();
		}catch(RemoteException e){
			e.printStackTrace();
		}
	}
	
	private void runRMIServer() throws RemoteException {
		log.info("Levantando servidor RMI...");
		Registry registryCli = LocateRegistry.createRegistry(this.rmiPortCli);
		Registry registrySv = LocateRegistry.createRegistry(this.rmiPortSv);
		IFTPManager remote = (IFTPManager) UnicastRemoteObject.exportObject(new FTPManager(this.ftpPort, this.ftp),0);
		IClient remoteClient = (IClient) UnicastRemoteObject.exportObject(this,0);
		IWorkerAction remoteWorker = (IWorkerAction) UnicastRemoteObject.exportObject(new WorkerAction(this.listaWorkers, this.listaTrabajos),0);
		registrySv.rebind("Acciones", remote);
		registrySv.rebind("server", remoteWorker);
		registryCli.rebind("client", remoteClient);
		log.info("Servidor RMI: ON");
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void readConfigFile() {
		Gson gson = new Gson();
		Map config;
		try {
			config = gson.fromJson(new FileReader(myDirectory+"serverConfig.json"), Map.class);
			Map data = (Map) config.get("rmi");
			this.rmiPortCli = Integer.valueOf(data.get("portCli").toString());
			this.rmiPortSv = Integer.valueOf(data.get("portSv").toString());
			
			data = (Map) config.get("ftp");
			this.ftpPort = Integer.valueOf(data.get("port").toString());
			this.myFTPDirectory = this.myDirectory + data.get("directory").toString();
			
			data = (Map) config.get("queue");
			this.queueIp = data.get("ip").toString();
			this.queuePort = data.get("port").toString();
			this.queueUser = data.get("user").toString();
			this.queuePwd = data.get("pass").toString();
			
		} catch (IOException e) {
			log.info("Error Archivo Config!");
		} 
	}

	private void initialConfig() {
		//QUEUE RELATED
		try {
			// [STEP 0] - FACTORIA DE CONEXION
			this.queueConnectionFactory = new ConnectionFactory();
			this.queueConnectionFactory.setHost(this.queueIp);
			this.queueConnectionFactory.setPort(Integer.valueOf(this.queuePort));
			this.queueConnectionFactory.setUsername(this.queueUser);
			this.queueConnectionFactory.setPassword(this.queuePwd);
			// [STEP 1] - QueueConnection
			this.queueConnection = this.queueConnectionFactory.newConnection();
			// [STEP 2] - ChannelConnection
			this.queueChannel = this.queueConnection.createChannel();
			// [STEP 3] - Create the queues
			this.queueChannel.queueDeclare(this.queueTrabajo, true, false, true, null);
			this.queueChannel.queueDeclare(this.queueTerminados, true, false, false, null);
			log.info("RabbitMQ inicio correctamente.");
		} catch (IOException e) {
			log.error("Error: Compruebe si RabbitMQ esta instalado en su equipo.");
		} catch (TimeoutException e) {
			log.error("Error: Time out.");
		}
		
		/*
	     * Eliminio las colas y las vuelvo a crear para borrar total
	     */
	    try {
			this.queueChannel.queueDelete(this.queueTrabajo);
		    this.queueChannel.queueDelete(this.queueTerminados);
			this.queueChannel.queueDeclare(this.queueTrabajo, true, false, true, null);
			this.queueChannel.queueDeclare(this.queueTerminados, true, false, false, null);
	    } catch (Exception e1) {
			log.error(e1.getMessage());
		}
		
		//FTP RELATED
		this.ftp = new ServerFtp(this.ftpPort, this.myFTPDirectory);
		log.info("FTP Configurado correctamente. Listo para usar en puerto:"+this.ftpPort+". Compartiendo carpeta: "+this.myFTPDirectory);
	}

	public static void main(String[] args) {
		new Servidor();
	}

	@Override
	public Imagen renderRequest(Mensaje msg) throws RemoteException {
		Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
		log.debug("Current thread launcheds: "+threadSet.toString());
		BufferedImage respuesta = null;
		this.listaTrabajos.add(msg.getName()+":"+msg.ipCliente);
		ThreadServer thServer = new ThreadServer(msg, listaWorkers, respuesta, this.queueChannel, this.queueConnection);
		Thread th = new Thread(thServer);
		th.start();
		while(thServer.getRespuesta() == null) {
			try {
				Thread.sleep(5000);
				log.debug("----"+Thread.currentThread()+"----");
				log.debug("Lista trabajo: "+this.listaTrabajos.toString());
				log.debug("Realizando trabajo: "+msg.getName());
				log.debug("----------------------------------");
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		this.listaTrabajos.remove(msg.getName()+":"+msg.ipCliente);//Experimental 
		th.interrupt();
		return new Imagen(thServer.getRespuesta());
	}


}
