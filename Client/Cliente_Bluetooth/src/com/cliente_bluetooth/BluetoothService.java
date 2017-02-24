package com.cliente_bluetooth;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class BluetoothService {
    // Variables para el proceso de depuración
    private static final String TAG = "BluetoothChatService";
    private static final boolean D = true;
 
    private static final String SDcard_PLACEMENT ="fromSdCard";
   
    // UUID única para esta aplicación
    private static final UUID MI_UUID_SEGURA =
        UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");
    private static final UUID MI_UUID_INSEGURA =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // Member fields
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    //private AcceptThread mSecureAcceptThread;
    //private AcceptThread mInsecureAcceptThread;
    private HiloDeConexion mHiloDeConexion;
    private HiloConectado mHiloConectado;
    private int mState;
    
    boolean RecibiendoDirectorio = true;

    // Constants that indicate the current connection state
    public static final int ESTADO_NINGUNO = 0;       // we're doing nothing
    public static final int ESTADO_CONECTANDO = 1; // now initiating an outgoing connection
    public static final int ESTADO_CONECTADO = 2;  // now connected to a remote device
    
    /**
     * Constructor. Prepara el Servicio BluetoothService.
     * @param context:  Context de la Interfaz de Usuario
     * @param handler:  Un Handler para comunicarse con la Activity Cliente_Bluetooth
     */
    public BluetoothService(Context context, Handler handler) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = ESTADO_NINGUNO;
        mHandler = handler;
    }
    
   

    /**
     * Se establece el estado actual de la conexion
     * @param Estado: muestra el estado de la conexion
     */
    private synchronized void EstablecerEstado(int Estado) {
        if (D) Log.d(TAG, "EstablecerEstado() " + mState + " -> " + Estado);
        mState = Estado;

        // Da el nuevo estado al Hndler para que la Interfaz de usuario lo pueda actualizar
        mHandler.obtainMessage(Cliente_Bluetooth.MENSAJE_CAMBIO_ESTADO, Estado, -1).sendToTarget();
    }

    /**
     * Devuelve el estado de la conexión
     **/
    public synchronized int getState() {
        return mState;
    }

    /**
     * Inicialzia el BluetoothService 
     */
    public synchronized void start() {
        if (D) Log.d(TAG, "start");

        // Cancela cualquier hilo que intentase realizar una conexion
        if (mHiloDeConexion != null) {mHiloDeConexion.cancel(); mHiloDeConexion = null;}

        // Cancela cualquier hilo de conexion
        if (mHiloConectado != null) {mHiloConectado.cancel(); mHiloConectado = null;}

    }

    /**
     * Inicializa HiloConexion para establacer una conexión con el Servidor.
     * @parametro device  -> Dispositivo Bluetooth para establecer la conexion
     * @param secure -> Seguridad del socket: Seguro (true) , Inseguro (false)
     */
    public synchronized void Conectar(BluetoothDevice Dispositivo, boolean Seguridad) {
        if (D) Log.d(TAG, "connect to: " + Dispositivo);

        // Se cancela cualquier hilo que estuviera ya en ese momento intentando realizar una conexion
        if (mState == ESTADO_CONECTANDO){
        	
            if (mHiloDeConexion != null){
            	
            	mHiloDeConexion.cancel(); 
            	mHiloDeConexion = null;
            }
        }

        // Se cancela cualquier hilo que ya tuviera una conexion Bluetooth
        if (mHiloConectado != null){
        		mHiloConectado.cancel(); 
        		mHiloConectado = null;
        }

        // Se inicializa el hilo que implementará la conexion con el servidor        
        mHiloDeConexion = new HiloDeConexion(Dispositivo, Seguridad);
        mHiloDeConexion.start();
        EstablecerEstado(ESTADO_CONECTANDO);
        
    }
    
    /**
     * Este hilo se ejecuta mientras se intenta realizar una conexión 
     * con el Servidor. Se ejecutará tanto si la conexión tiene éxito o si 
     * algo falla.
     */
    private class HiloDeConexion extends Thread {
    	
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private String mSocketType;

        @SuppressLint("NewApi")
		public HiloDeConexion(BluetoothDevice device, boolean secure) {
        	
            mmDevice = device;
            BluetoothSocket tmp = null;
            mSocketType = secure ? "Secure" : "Insecure";

            //Se crea un socket Bluetooth para establacer la conexion con el Servidor
            try {
                if (secure) {
                	
                    tmp = device.createRfcommSocketToServiceRecord(MI_UUID_SEGURA); 
                    
                } else {          
                	
                    tmp = device.createInsecureRfcommSocketToServiceRecord(MI_UUID_INSEGURA);
                    
                }
            } catch (IOException e) {
            	
                Log.e(TAG, "Socket Type: " + mSocketType + "create() failed", e);
                
            }
            
            mmSocket = tmp;
            
        }

        public void run() {
        	
            Log.i(TAG, "BEGIN mHiloDeConexion SocketType:" + mSocketType);
            setName("HiloDeConexion" + mSocketType);

            // Se cancela cualquier proceso de descubrimiento que estuviera realizando el adaptador
            mAdapter.cancelDiscovery();

            //Se establace la conexion con el Servidor a través del socket
            try {
            	
                mmSocket.connect();
                
            } catch (IOException e) {

            	//En el caso de que se produzca una excepción se cierra el socket
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() " + mSocketType +" socket during connection failure", e2);
                            
                }
                ErrorConexion();
                return;
                
            }

            // Se resetea HiloConectar
            synchronized (BluetoothService.this) {
                mHiloDeConexion = null;
            }

            //Se inicializa el hilo Conectado
            Conectado(mmSocket, mmDevice, mSocketType);
        }

        //Si se cancela el hilo, se cerrará el Socket
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect " + mSocketType + " socket failed", e);
            }
        }
    }
    

    /**
     * Inicializa el hilo HiloConectado para comenzar a manejar una conexion Bluetooth
     * @param Socket  El BluetoothSocket a través del cual se establecerá la conexion
     * @param DispositivoBT  El BluetoothDevice al que se conectará la aplicación
     * @Param TipoSocket: Tipo de conexion que se establecerá (Segura o Insegura)
     */
    
    public synchronized void Conectado(BluetoothSocket Socket, BluetoothDevice DispositivoBT, final String TipoSocket) {
    	
        if (D) Log.d(TAG, "connected, Socket Type:" + TipoSocket);

        // Cancela el hilo que se utilizó para completar la conexión
        if (mHiloDeConexion != null) {mHiloDeConexion.cancel(); mHiloDeConexion = null;}

        // Cancela cualquier hil que estuviera llevando a cabo una conexión 
        if (mHiloConectado != null) {mHiloConectado.cancel(); mHiloConectado = null;}

        // Inicia el hilo para gestionar las conexiones y llevar a cabo las transmisiones
        mHiloConectado = new HiloConectado(Socket, TipoSocket);
        mHiloConectado.start();

        // Se envía el nonbre del Servidor a la Interfaz de Usuario del Activity Cliente_Bluetooth
        Message MensajeParaActivity = mHandler.obtainMessage(Cliente_Bluetooth.MENSAJE_CONECTADO);
        Bundle bundle = new Bundle();
        bundle.putString(Cliente_Bluetooth.DEVICE_NAME, DispositivoBT.getName());
        MensajeParaActivity.setData(bundle);
        mHandler.sendMessage(MensajeParaActivity);

        EstablecerEstado(ESTADO_CONECTADO);
        
    }

    
    /**
     * Este hilo se ejecuta durante una conexion para gestionar tanto las transmisiones entrantes como salientes
     */
    private class HiloConectado extends Thread {
    	
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private OutputStream fo = null;
        private byte[] BufferRecepcion;
        private byte[] BufferEnvio;
        int NumeroBytesRecibidos;
        
        private boolean FlagEnviarAhora = false;
               
        private int k=0;

        
        private boolean EndFat=false;

        //El cosntructor creará los streams de datos, tanto de entrada como de salida
        public HiloConectado(BluetoothSocket socket, String socketType) {
            Log.d(TAG, "create HiloConectado: " + socketType);
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
              	
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
                
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut; 

        }

        public void run() {
        	
            Log.i(TAG, "BEGIN mHiloConectado");
            
            BufferRecepcion = new byte[1024];
            BufferEnvio = new byte[2048];
            RecibiendoDirectorio = true;
            	
            // Keep listening to the InputStream while connected
            while (true) {
            	
                try {
                                	
                	// Se leen los bytes enviados por el Servidor entreo de un buffer
                	NumeroBytesRecibidos = mmInStream.read(BufferRecepcion);
                                      
                	// En caso de que se esté recibidendo el contenido de un Directorio
                    if(RecibiendoDirectorio)
                    {
                    	AlmacenarNombre();
                    	
                    	if(FlagEnviarAhora)
                        {
                        	mHandler.obtainMessage(Cliente_Bluetooth.MENSAJE_ELEMENTO, k, -1, BufferEnvio).sendToTarget();
                        	FlagEnviarAhora=false;
                        	
                        	byte[] retorno = new byte[1];

                        	mHandler.obtainMessage(Cliente_Bluetooth.MENSAJE_BYTES_RECIBIDOS, k, -1, retorno).sendToTarget();
                        	
                        	k=0;
                        }
                    	
                    // En caso de que se esté recibiendo el contenido de un archivo	
                    }else{
                    	
                    	k=0;
                    	
                    	AlmacenarArchivo();
                    	
                    	byte[] retorno = new byte[1];                   	
                    	
                    	mHandler.obtainMessage(Cliente_Bluetooth.MENSAJE_BYTES_RECIBIDOS, k, -1, retorno).sendToTarget();
                    	k=0;
                    }                  
                    
                 
                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    ConsexionPerdida();
                    break;
                }
            }
        }

        // Este método creará un archivo en el Directorio Local a partir de los bytes almacenados 
        // en NombreArchivoCrear
        
        public void CrearArchivo(byte[] NombreArchivoCrear)
        {
        	String StringNombreArchivoCrear = new String(NombreArchivoCrear);
        	
        	String RutaArchivo = Environment.getExternalStorageDirectory().getPath();
            File NuevoDirectorio = new File(RutaArchivo,SDcard_PLACEMENT);
            
            NuevoDirectorio.mkdirs();
                     
            File NuevoArchivo = new File(NuevoDirectorio.getAbsolutePath() + "/" +StringNombreArchivoCrear);
             
            try {
             	
            	NuevoArchivo.createNewFile();
 				
 			} catch (IOException e1) {
 				// TODO Auto-generated catch block
 				e1.printStackTrace();
 			}
             
         	//write the bytes in file
         	if(NuevoArchivo.exists())
         	{
         	     try {
 					
         	    	 	fo = new FileOutputStream(NuevoArchivo);
         	    	 	
 				} catch (FileNotFoundException e) {
 					// TODO Auto-generated catch block
 					e.printStackTrace();
 				}              
         	     
         	} 
        	

        }
        
        // Este método determinará los nombres de los diferentes elementos disponibles en el directorio
        // en el que se encuentra el usuario.
        
        public void AlmacenarNombre()
        {
        	
        	for(int ContadorBytesBucle=0; ContadorBytesBucle < NumeroBytesRecibidos; ContadorBytesBucle++) 
        	{     
        	
        		// Cuando se reciba el caracter 7, se considera que se han enviado toda la información
        		// de los elementos almacenados en el directorio del Server en el que se encuentra el Usuario 
        		if((int)BufferRecepcion[ContadorBytesBucle]== 7)
				{
        			
        			mHandler.obtainMessage(Cliente_Bluetooth.MENSAJE_INDICE, 0, -1, 0).sendToTarget();       			
    				RecibiendoDirectorio=false;
    				return;
    				
				}
        		
        		// Cuando se reciba el caracter 4, se considera que se ha recibido completamente la información de uno
        		// de los elementos (nombre completo + tamaño). Se preparan los flag para enviar dicha información
        		// al Activity Cliente_Bluetooth
        		if((int)BufferRecepcion[ContadorBytesBucle]== 4)
				{
        			FlagEnviarAhora=true;        			
        			return;
        					
				}
        		
        		BufferEnvio[k++] = BufferRecepcion[ContadorBytesBucle];
        		
        	}    	
        	
        }
        
        //Este método es invocado cuando se está descargando un archivo del Servidor
        //irá almacenando los bytes recibido en el archivo correspondiente
        public void AlmacenarArchivo()
        {        
        	
        	for(int ContadorBytesBucle = 0; ContadorBytesBucle < NumeroBytesRecibidos; ContadorBytesBucle++) 
        	{  

        		try {     
					
					fo.write(BufferRecepcion[ContadorBytesBucle]);
					k++;
					
				} catch (IOException e) {
					
					e.printStackTrace();
				}
        		
        	}
       	  	
        	
        }

        
        
         // EnviarComando envía un comando a través del OutStream.
         // @param Comando: bytes del comando a enviar
         
        public void EnviarComando(byte[] Comando) {
            try {
                mmOutStream.write(Comando);
             
                       
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }
    
    
    
    /**
     * Este método detiene todos los hilos activos
     */
    public synchronized void stop() {
        if (D) Log.d(TAG, "stop");

        if (mHiloDeConexion != null) {
            mHiloDeConexion.cancel();
            mHiloDeConexion = null;
        }

        if (mHiloConectado != null) {
            mHiloConectado.cancel();
            mHiloConectado = null;
        }

       
        EstablecerEstado(ESTADO_NINGUNO);
    }

    
    /**
     * Write to the HiloConectado in an unsynchronized manner
     * @param out The bytes to write
     * @see HiloConectado#write(byte[])
     */
    
    //Esto hay que hacerlo mejor
    public void SeEsperaDirectorio(){
    	
    	RecibiendoDirectorio = true;
    	
    }
    
    
    /**
     * Metodo para utilizar el hilo mHiloConectado para enviar
     * bytes a través del sockect Bluetooth, de manera desincronizada
     *  @param Enviar: los bytes a escribir
     */
    
    public void EnviarComando(byte[] BytesEnviar) {
        // Create temporary object
        HiloConectado HiloTemporalEnviar;
        // Synchronize a copy of the HiloConectado
        synchronized (this) {
            if (mState != ESTADO_CONECTADO) return;
            HiloTemporalEnviar = mHiloConectado;
        }
        // Perform the write unsynchronized
        System.out.println("Mandando X");
        HiloTemporalEnviar.EnviarComando(BytesEnviar);
    }
    
    /**
     * Metodo para utilizar el hilo mHiloConectado para crear un
     * nuevo archivo en el Directorio Local, de manera desincronizada
     * @param NombreArchivo: El nombre del archivo a crear
     */
    
    public void CrearArchivo(byte[] NombreArchivo) {
    	
        // Se crea un objeto temporal
        HiloConectado HiloTemporal;
        // Se sincroniza con el hilo mHiloConectado
        synchronized (this) {
            if (mState != ESTADO_CONECTADO) return;
            HiloTemporal = mHiloConectado;
        }
        // Se crea de forma paralela el archivo en la memoria
        HiloTemporal.CrearArchivo(NombreArchivo);
    }

    /**
     * En caso de que no se pueda conectar con el Servidor, se envia 
     * una notificación a la Interfaz de Usurio de Cliente_Bluetooth
     */
    private void ErrorConexion() {
    	// Se prepara el mensaje a enviar al Handler
        Message msg = mHandler.obtainMessage(Cliente_Bluetooth.MENSAJE_SALUDO);
        Bundle bundle = new Bundle();
        bundle.putString(Cliente_Bluetooth.TOAST, "No es posible establecer conexion con el Servidor");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

     // Se resetea el BluetoothService
        BluetoothService.this.start();
    }

    /**
     * En caso de que se pierda la conexion entre el dispositivo portátil
     * y el Servidor, se envia una notificación a la Interfaz de Usurio de Cliente_Bluetooth
     */
    private void ConsexionPerdida() {
    	
        // Se prepara el mensaje a enviar al Handler
        Message msg = mHandler.obtainMessage(Cliente_Bluetooth.MENSAJE_SALUDO);
        Bundle bundle = new Bundle();
        bundle.putString(Cliente_Bluetooth.TOAST, "Se perdió la conexión con el Servidor");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        // Se resetea el BluetoothService
        BluetoothService.this.start();
    }

    
}

