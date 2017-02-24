package com.cliente_bluetooth;

import java.io.File;
import java.util.Set;



import android.text.InputType;
import android.net.Uri;
import android.os.Bundle;

import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;


public class Cliente_Bluetooth extends ListActivity {
		
	//Definici�n de variables globales
	
	int ContadorDirectoriosInvisibles = 0;	
	
	int IndiceListaRemota;
	
	Context Contexto = this;
		
	String NombreViejo, NombreArchivo;
	
	private boolean ComprobandoBluetooth = true;
	
	String MensajeParaServidor;
	
	int LongitudArchivoSeleccionado = 0;
	Integer[] ArrayLongitudes = new Integer[127];
    String[] ArrayNombres = new String[127];
	
    private boolean FlagDirectorioArchivo = false;
       
	public int ContadorBytesTotales = 0;
	public int ContadorBytesSegundo = 0;
    
    public int IndiceDirectorio = 0;
    
	// C�digo de petici�n del Intent
	private static final int REQUEST_ENABLE_BT = 1;
	
	// Mensajes enviados por el BluetoothService al Handler 
	public static final int MENSAJE_CAMBIO_ESTADO = 1;
    public static final int MENSAJE_ELEMENTO = 2;
    public static final int MENSAJE_CONECTADO = 3;
    public static final int MENSAJE_SALUDO = 4;
    public static final int MENSAJE_BYTES_RECIBIDOS = 5;
    public static final int MENSAJE_INDICE = 6;

    
    // Variables usadas por el Handler y por el Service BluetoothService
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";
    
    
    // Elementos que forman parte del layout
    private ArrayAdapter<String> mListaArchivosRemotosAdaptador;
    private ArrayAdapter<String> mListaArchivosDPAdaptador;
    private ListView ListaArchivosDPListView;
    Button BotonEnviar;
    
    // Elementos que controlan la actualizaci�n de la la barra de progeso 
    // al descargar archivos
    private ProgressDialog DescargandoPD;
    Handler mBarraProgreso;
   
    //Objeto BluetoothService
    private BluetoothService mBluetoothService = null;

    // Adaptador Bluetooth
    private BluetoothAdapter mBluetoothAdapter = null;
    
    // ProgressDialog que aparece durante el proceso de conexion
    private ProgressDialog mConectandoPD;

    //Hilo que se encargar� de informar sobre el estado de la descarga
    private HiloDescarga mHiloDescarga;
   
    
    /**     
     * En el M�todo onCreate() se declaran e inicializan los elementos que intervendr�n en la conexi�n entre
     * la Aplicaci�n de Usuario y el Servidor.
     */
    
    
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.activity_cliente__bluetooth);
		
		//Se preparan los par�metros de un  ProgressDialog para indicar al usuario que se est� intentando conectar
		//con el Servidor.
		
		mConectandoPD = new ProgressDialog(Cliente_Bluetooth.this);
		mConectandoPD.setIndeterminate(false);
		mConectandoPD.setMessage("Conectando con Servidor");
		mConectandoPD.setProgressStyle(ProgressDialog.STYLE_SPINNER);		   
	   
		
		//Se declara el BluetoothAdapter y se comprueba si el terminal m�vil soporta Bluetooth
		//En caso de no ser as� se muestra un mensaje y se cierra la aplicaci�n
		
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		
		if (mBluetoothAdapter == null) {
			
			Toast.makeText(Cliente_Bluetooth.this, "Este dispositivo no soporta Bluetooth", Toast.LENGTH_SHORT).show();
			finish();
            return;
            
		}
		
		//Se inicializa el Handler que mostrar� *ProgressBar* de la descarga de archivos
		mBarraProgreso = new Handler();
		
		//Se inicializa el servicio BluetoothService, vinculado a esta aplicaci�n y al Handler mHandler
		mBluetoothService = new BluetoothService(this, mHandler);

		
	}
	
	/**
	 * A continuaci�n se definen los m�todos que forman parte del lifecycle de cualquier aplicaci�n Android
	 */
	
	public void onStart() {
    	
		 super.onStart();
		 		 
		//Antes de intentar establecer una conexioon con el Servidor, se comprueba si est� activado el Bluetooth
		 
		 if(ComprobandoBluetooth)
		 {
			 
			 ComprobandoBluetooth = false;
			 
		       //En caso de no estar activado se le solicitar� al usuario que lo active
		       if (!mBluetoothAdapter.isEnabled()) {
		        	
		            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
		            
		            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
		            
		            boolean EsperarPorBT = true;
		            
		            do{
		            	
		            	if (mBluetoothAdapter.isEnabled()) {
		            		
		            		//Una vez que el dispositivo tenga activado su m�dulo Bluetooth
		            		//se realizar� la conexi�n
		            		ConectarConServidor();
		            		EsperarPorBT = false;
		            	}
		            	
		            }while(EsperarPorBT);
		            
		            
		        //En caso de que si tuviera activado el m�dulo Bluetooth se realizar� la conexi�n
		        } else {
		        	
		            ConectarConServidor();
		            
		       }
		       
		 }        

	}
	
	public void onStop()
	{
		//Se avisa al Servidor que se cierra la aplicaci�n para que cancele la transferencia
		 if (mHiloDescarga != null) {
			 
			 mHiloDescarga.CancelarTransferencia();
			 
		 }
		super.onStop();				
	}
	
	
	public void onDestroy() 
	{
		//Se avisa al Servidor que se cierra la aplicaci�n y que el usuario cierra la conexi�n
		EnviarMensaje("adi000");
        super.onDestroy();
    }
	
	@Override
	public void onPause() {
		
	    super.onPause();  
	}
	
	
	public void onResume(){
		
		super.onResume();		
	}
	
	
	/**
	 * En este m�todo se establece la conexion con el Seridor a trav�s de Bluetooth
	 */
	
	public void ConectarConServidor()
	{		
		
		//Se extrae la lista de dispositivos Bluetooth emparejados con el terminal m�vil
	    Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
		
	    
		if (pairedDevices.size() > 0) {
			
			for (BluetoothDevice device : pairedDevices) {
				
			   //Si en la lista est� el m�dulo Bluetooth del Servidor, cuyo nombre es "EPBMX-COM", se establece la conexi�n
	    	   if(device.getName().equals("EPBMX-COM")){
		       		    		   
	    		   	BluetoothDevice Connectdevice = mBluetoothAdapter.getRemoteDevice(device.getAddress());
	    		   	  
	    		   	//El BluetoothServive establece una conexi�n insegura con el Servidor
	    		   	mBluetoothService.Conectar(Connectdevice, false);   	        
		
	    	   }	        	   
			}			
		}
		
		//Se muestra el ProgressDialog conectando con el Servidor
		mConectandoPD.show();
		
	}
	 
	
	/**
	 * Una vez se haya realizado exitosamente el proceso de conexion se generan
	 * e inicializan los elementos que formar�n el layout
	 */
	
	private void ConexionEstablecida(){
		   
	   mListaArchivosRemotosAdaptador = new ArrayAdapter<String>(this, R.layout.message);
		
       mListaArchivosDPAdaptador = new ArrayAdapter<String>(this, R.layout.device_name);
       ListaArchivosDPListView = (ListView) findViewById(R.id.arhivos_dp); 
       ListaArchivosDPListView.setAdapter(mListaArchivosDPAdaptador);
       ListaArchivosDPListView.setOnItemClickListener(mArchivosDPClickListener);
       
       
       
       //Se hacen visibles los titulos de las dos secciones
       findViewById(R.id.title_archivos_remotos).setVisibility(View.VISIBLE);
       findViewById(R.id.title_nuestros_archivos).setVisibility(View.VISIBLE);
       
       
       
       //Se have visible el boton
       BotonEnviar = (Button) findViewById(R.id.button_send);
       BotonEnviar.setOnClickListener(new OnClickListener() {
           public void onClick(View v) {
        	   
        	   
        	   //mConversationArrayAdapter.notifyDataSetChanged();
        	   
        	   if(!FlagDirectorioArchivo)
        	   {   
        		   mListaArchivosRemotosAdaptador.clear();
        		   EnviarMensaje("fat000"); 
 	   
        	   }else
        		   
        		   MostrarContenidoDP();
        		   
           }
       });
          
       
       BotonEnviar.setVisibility(View.VISIBLE);
       
       MostrarContenidoDP();
       
       
	}
	

	/**
	 * A continuaci�n se definen los OnItemClickListeners para las dos listas de la aplicaci�n Usuario
	 */
	
	/**
	 * 1- El primer onClickListener es para la lista de archivos remotos. Al seleccionar uno se descargar� su contenido
	 * en el terminal m�vil. En caso de ser un directorio, se actualizar� la ventana superior de la aplicaci�n 
	 * con el contenido del nuevo directorio seleccionado.
	 */
	   private OnItemClickListener mArchivosRemotosClickListener = new OnItemClickListener() {
		   
	       public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
	    	   
	    	   IndiceListaRemota = arg2+1;

	    	   MensajeParaServidor = "gfl";
	           
	           IndiceListaRemota += ContadorDirectoriosInvisibles;
	           
	           //Se calcula cu�ntos ceros hay que incluir en el comando que se le env�a al
	           //servidor en funci�n del �ndice del elemento seleccionado
	           if(IndiceListaRemota>100)
	        	   MensajeParaServidor += IndiceListaRemota;
	           else if(IndiceListaRemota>10)
	        	   MensajeParaServidor +="0"+IndiceListaRemota;
	           else
	        	   MensajeParaServidor +="00"+IndiceListaRemota;
	           
	           
	           //En los directorios anidados existen una serie de directorios "invisibles" que sirven para
	           //denotar el directorio ra�z y el directorio padre.
	           IndiceListaRemota -= ContadorDirectoriosInvisibles;
	           
	           //Se extrae el nombre del archivo
	           NombreArchivo = ArrayNombres[IndiceListaRemota]; 
	           
	           
	           //Si el nombre del elemento seleccionado es "..", significa que el Usuario desea volver
	           //al directorio padre
	           if((NombreArchivo.contains("..")) && (NombreArchivo.length() == 2) )
	           {
	        	      	   
	        	   ContadorDirectoriosInvisibles = 0;
	        	   
	        	   //Se prepara el comando de regreso al directorio padre
	        	   MensajeParaServidor = "bkd000";

	        	  //Se borra el contenido de la ventana superior para cargar los elementos
	        	  //del directorio padre
	        	   mListaArchivosRemotosAdaptador.clear();
	        	   mListaArchivosRemotosAdaptador.notifyDataSetChanged();
	        	   
	        	   IndiceDirectorio=0;
	        	   
	        	   //Se prepara el BluetoothService para que reciba el contenido de un directorio
	        	   mBluetoothService.SeEsperaDirectorio();
	        	   EnviarMensaje(MensajeParaServidor);
	        	   
	           }
	           
	           //En caso de que el nombre del elemento seleccionado no contenga el caracter ".",
	           //implica que el usuario seleccion� un directorio
	           else if( NombreArchivo.indexOf(".") <= 0) 
	           {
	        	 
	        	   ContadorDirectoriosInvisibles = 0;
	        	   
	        	  //Se borra el contenido de la ventana superior para cargar los elementos
		          //del nuevo directorio
	        	   mListaArchivosRemotosAdaptador.clear();
	        	   mListaArchivosRemotosAdaptador.notifyDataSetChanged();
	        	   
	        	   IndiceDirectorio=0;
	        	   
	        	  //Se prepara el BluetoothService para que reciba el contenido de un directorio
	        	   mBluetoothService.SeEsperaDirectorio();
	        	   EnviarMensaje(MensajeParaServidor);
	        	   
	           }else{
	        	   
	        	   //En caso de no tratarse de ninguna de las opciones anteriores, el archivo seleccionado es un 
	        	   //archivo
	        	   
	        	   //Se almacena el nombre del archivo en una variable
	        	   NombreViejo = NombreArchivo;
		           
		           //Se comprueba si ya existe en la memoria del telef�no un archvio con dicho nombre
		           if(RevisarExistenciaArchivo(NombreArchivo))
		           {
		        	   //En caso de que exista un archivo con dicho nombre se le ofrece al usuario la opci�n
		        	   //de renombrarlo
		        	   RenombrarArchivoEnMemoria(true);
		        	   
		           }
		           else
		           {
		        	   //Si no existe un archivo con ese nombre se crea uno nuevo, en el que se almacenar�n
		        	   //todos los datos que env�e el Servidor
		        	   CrearNuevoArchivo(NombreArchivo);

		           }  
	        	   
	           }           
	           
	       }
	       
	   };
	   
	   
	   /**
		 * 2- El segundo onClickListener es para la lista de archivos locales. Una vez seleccionado uno
		 * el Usuario tendr� la opci�n de abrirlo, renombrarlo o eliminarlo
		 */
	   
	   private OnItemClickListener mArchivosDPClickListener = new OnItemClickListener() {
		   
	       public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
	    	   
	    	    //En esta variable se almacenar� el nombre del arhivo seleccionado
	    	   	final String NombreArchivoLocal = ((TextView) v).getText().toString();
	    	   
	    	   	//Secuencia de strings con las opciones que ofrecer� el submenu	
	    	   	final CharSequence[] OpcionesMenu = {"Abrir", "Renombrar", "Eliminar"};
	    	        
    	   		//Se crea un submen� con las opciones disponibles para cada archivo
    		    AlertDialog.Builder builder = new AlertDialog.Builder(Cliente_Bluetooth.this);	    
    		    builder.setTitle("�Qu� desea hacer con el archivo?")
    		           .setItems(OpcionesMenu, new DialogInterface.OnClickListener() {
    		               public void onClick(DialogInterface dialog, int which) {

			            	   //En funci�n de la opci�n elegida se invocar� uno de los m�todos listados a continuaci�n
			            	   switch(which)
			            	   {
			            	   		case 0: AbrirArchivoEnMemoria(NombreArchivoLocal); break;
			            	   		case 1: NombreViejo = NombreArchivoLocal; RenombrarArchivoEnMemoria(false); break;
			            	   		case 2: EliminarArchivoEnMemoria(NombreArchivoLocal); break;
			            	   }
    		            	   
    		           }
    		               
    		    });
	   
	    		//Se muestra el submenu
	    		builder.show();  	   
	    	  
	       }
	       
	   };  
	   
	   /**
	    * M�todos utilizados por el primer OnClickListener, para revisar si ya existe 
	    * en memoria un archivo con el nombre seleccionado y actuar en funci�n del resultado
	    */
	 
	   /**
	    * 1- Este m�todo comprueba si ya existe en memoria un archivo con el nombre introducido
	    * @param NombreActual -> Nombre que se desea comprobar
	    * @return true: Existe un archivo llamado, "NombreActual" en el Directorio Local
	    *         false: No existe un archivo llamado "NombreActual" en el Directorio Local
	    */
	   private boolean RevisarExistenciaArchivo(String NombreActual)
	   {
		   String NombreFichero;
		   
		   File sdCardRaiz = Environment.getExternalStorageDirectory();	
	       File DirectorioLocal = new File(sdCardRaiz, "/fromSdCard");     
	
	        for (File FicheroAuxiliar : DirectorioLocal.listFiles()) {
	     		
	           if (FicheroAuxiliar.isFile())
	            {    
	        	   NombreFichero = FicheroAuxiliar.getName();
	                
	            	if(NombreFichero.equals(NombreActual))
	            	{
	            		//Ya existe un Archivo con este nombre en el Directorio Local
	            		return true;
	            	}	            	
	           
	            }
	    
	        }
	        
	        return false;
		   
	   }
	   
	   
		/**
		 * 2- Este m�todo crea en el Directorio Local un nuevo Archivo llamado "NombreNuevoArchivo"
		 * @param NombreNuevoArchivo: Ser� el nombre del nuevo archivo que se crear� 
		 * en el Directorio Local.
		 */
		
		
		private void CrearNuevoArchivo(String NombreNuevoArchivo) {
						
	        // Antes de crear el nuevo Archivo se comprueba que el Usuario est� conectado al Servidor
	        if (mBluetoothService.getState() != BluetoothService.ESTADO_CONECTADO) {
	        	
	            Toast.makeText(this, "El dispositivo no est� conectado", Toast.LENGTH_SHORT).show();         
	            return;
	        }
	        
	        if (NombreNuevoArchivo.length() > 0) {
	        	
	            // Se convierte en bytes NombreNuevoArchivo
	            byte[] NombreBytes = NombreNuevoArchivo.getBytes();
	            
	            //Se env�an dichos bytes al Service BluetoothService
	            mBluetoothService.CrearArchivo(NombreBytes);
	            
	            //Se prepara el Hilo que recibir� los nuevos datos
	            ActivarHilo(IndiceListaRemota);
	            
	        }	        
	    
	    }
	   
	   
	   /**
	    * M�todos utilizados por el segundo OnClickListener, para determinar que operaci�n
	    * se realiza con el archivo seleccionado
	    */
	   
	   /**
	    * 1- Abrir el archivo que se encuentra en el Directorio Local
	    */
	   
	   private void AbrirArchivoEnMemoria(String NombreArchivo)
	   {
		   //Se obtiene la ruta en la que se encuentra el archivo
    	   File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/fromSdCard/"+NombreArchivo);
    	   
    	   //Se crea un intent para arbir el archivo
    	   Intent intent = new Intent(Intent.ACTION_VIEW);
    	   
    	   //En funci�n del tipo de archivo se escoger� un programa u otro
    	   if(NombreArchivo.contains(".txt") | NombreArchivo.contains(".TXT"))
    		   intent.setDataAndType(Uri.fromFile(file), "text/plain"); 
    	   
    	   else if(NombreArchivo.contains(".pdf") | NombreArchivo.contains(".PDF"))
    		   intent.setDataAndType(Uri.fromFile(file), "application/pdf"); 
    		   
    	   intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
    	   startActivity(intent);   	
		   
	   }
	   
	   /**
	    * 2- Este m�todo renombra el arhivo seleccionado por el usuario
	    * @Descargando: true -> Se pretende descargar un archivo y hay que renombrarlo porque ya existe otro con ese nombre
	    * 				false -> Se renombra un archivo almacenado en el Directorio Local 
	    */
	   private void RenombrarArchivoEnMemoria(final boolean Descargando)
	   {
		   	 
		    final String NombreViejoSinExtension;
		   
		    //Se muestra un AlertDialog preguntando al usuario c�mo desea renombrar el archivo
			AlertDialog.Builder alertDialog = new AlertDialog.Builder(Cliente_Bluetooth.this);
			
			if(Descargando)
			{
				alertDialog.setTitle("Ya existe en memoria un archivo llamado "+NombreViejo);
				alertDialog.setMessage("Introduce un nuevo nombre");
				
			}else{
				
				alertDialog.setTitle("�C�mo desea renombrar el archivo "+NombreViejo+"?");
			}
			 
			 
			//Para evitar que al renombrar el archivo se cambie la extensi�n, y por tanto el formato
			//se separar� el nombre y la extensi�n
			int PosDot = NombreViejo.indexOf(".");
	        NombreViejoSinExtension = NombreViejo.substring(0,PosDot);
	        final String ExtensionNombreNuevo = NombreViejo.substring(PosDot);
			 
	        //Se a�ade un campo de edici�n de texto al AlertDialog
			final EditText input = new EditText(Cliente_Bluetooth.this);
			 
			 LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
			     LinearLayout.LayoutParams.MATCH_PARENT,
			     LinearLayout.LayoutParams.MATCH_PARENT);
			 
			 input.setLayoutParams(lp);
			 alertDialog.setView(input);

			 //Se autocompleta el campo de edici�n con el nombre del archivo a modificar y
			 //se desactivan las correcciones ortogr�ficas
			 input.setText(NombreViejoSinExtension);
			 input.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
			 
			 //Se definenen las acciones a tomar cuando el usuario pulsa alguno de los dos
			 //botones: "Aceptar" y "Cancelar"
			 
			 alertDialog.setPositiveButton("Aceptar",
					 
			     new DialogInterface.OnClickListener() {
				 
				 String NuevoNombre;
				 String NombreNuevoSinExtension;
				 boolean SePuedeSeguir;
				 
			         public void onClick(DialogInterface dialog, int which) {
			        	 
			        	 SePuedeSeguir = true;
			        	 
			        	 //Se lee el nombre introducido en el Campo de edici�n del AlertDialog
			        	 NuevoNombre = input.getText().toString();
			        	 
			        	 //Se comprueba si el usuairo ha escrito una extensi�n en el nombre del archivo.
			        	 //En caso de ser as� descarta.
			        	 int PosDot = NuevoNombre.indexOf(".");		        	 
			        	 
			        	 if(PosDot>0)
			        	 	 NombreNuevoSinExtension = NuevoNombre.substring(0,PosDot);
			        	 else
			        		 NombreNuevoSinExtension = NuevoNombre;
			             
			             //En caso de que el usuario dejase el campo vac�a, se llama de nuevo al m�todo
			        	 if(NombreNuevoSinExtension.length()==0)
			        	 { 
			            	 dialog.cancel();
			            	 RenombrarArchivoEnMemoria(Descargando);
			            	 SePuedeSeguir=false;
			             }
			        	 
			        	 //En caso de que se haya vuelto a escribir el mismo nombre, se llama de nuevo al m�todo
			             if(NombreNuevoSinExtension.equals(NombreViejoSinExtension))
			             { 
			            	 dialog.cancel();
			            	 RenombrarArchivoEnMemoria(Descargando);
			            	 SePuedeSeguir=false;
			             }
			             
			             //En caso de tratarse de un nombre no vac�a y diferente se procede a realizar nuevas comprobaciones
			             if(SePuedeSeguir)
			             {
	 
				        	 String NombreAux;
				        	 String NombreAuxSinExtension;
				        	 
				        	 //La siguiente comprobaci�n consistir� en analizar el nombre de todos los archivos ya presentes en la
				        	 //memoria, para no renombrar el archuvo con el nombre de otro
				             File sdCardRoot = Environment.getExternalStorageDirectory();
				             File yourDir = new File(sdCardRoot, "/fromSdCard");     
		
				 	         for (File f : yourDir.listFiles()) {
				 	     		
					 	           if (f.isFile())
					 	            {    
					 	        	  NombreAux = f.getName();
					 	            	
					 	            	PosDot = NombreAux.indexOf(".");
					 	            	NombreAuxSinExtension = NombreAux.substring(0,PosDot);
					 	            	
					 	            	//Por precuaci�n se compara en mayusculas el nombre de cada archivo en el direcotrio del programa
					 	            	//con el nuevo nombre introducido por el Usuario.
					 	            	String StringNuevoUpper = NombreNuevoSinExtension.toUpperCase();
					 	            	String StringAuxUpper = NombreAuxSinExtension.toUpperCase();
					 	            			
					 	            	//En caso de intentar renombrar el archivo con un nombre ya existente, se vuelve a llamar al m�todo
					 	            	//RenombrarArchivoEnMemoria() pero actualizando el nombre de NombreViejo
					 	            	if(StringNuevoUpper.equals(StringAuxUpper))
						            	{	
					 	            		NombreViejo = NombreNuevoSinExtension + ExtensionNombreNuevo;
						            		dialog.cancel();		            		
						            		RenombrarArchivoEnMemoria(Descargando);
						            		SePuedeSeguir=false;
						            		break;
						            	} 	            	
		
					 	            }
				 	    
				 	         	}
				        	 
					 	       //En caso de que sea un nombre nuevo, se renombrar� el archivo correctamente y se actualizar� el contenido 
				 	           // de la segunda ventana de la aplicacion (El Directorio Local)
					 	       if(SePuedeSeguir)
					 	       {
					 	    	   String ExtensionLowerCase = ExtensionNombreNuevo.toLowerCase();
				            		
				            		if(MayusculasMinusculas(NombreNuevoSinExtension))
				            		{
				            			NombreArchivo = NombreNuevoSinExtension + ExtensionLowerCase;
				            		}
				            		else
				            		{
				            			NombreArchivo = NombreNuevoSinExtension + ExtensionNombreNuevo;
				            		}
					 	    	   
				            		
				         		   if(Descargando)
				         		   {
				         			   //En caso de que se estuviera descargando un nuevo archivo
				         			  CrearNuevoArchivo(NombreArchivo);
				       				
				         		   }else{
				         			   //En caso de que se estuviera renombrando un archivo ya existente en el Directorio Local
				         			  String NombreNuevo = NombreArchivo;
					         		  File from = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/fromSdCard/"+NombreViejo);
					         		  File to = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/fromSdCard/"+NombreNuevo);
					         		  from.renameTo(to);
					         		   
				         			  MostrarContenidoDP();
				         		   }
				         		   	
				         		   	
					 	    	   	
					 	       }			 	         
				 	         
			             }

			         }
			         
			     });

			 
			 //Se cancela la operaci�n y se cierra el AlertDialog
			 alertDialog.setNegativeButton("Cancelar",
			     new DialogInterface.OnClickListener() {
			         public void onClick(DialogInterface dialog, int which) {
			             dialog.cancel();
			         }
			     });
			 

			 //Se muestra el AlertDialog con todos sus campos y botones previamente definidos
			 alertDialog.show();
		   
	   }
	   
	   /**
	    * 3- Este M�todo elimina el archivo seleccionado del Directorio Local
	    */
	   
	   private void EliminarArchivoEnMemoria(String NombreEliminar)
	   {
		   
		   final String NombreAux = NombreEliminar;
		   
		   //Se mostrara un AlertDialog al usuario para que confirme si desea o no eleminar el archivo seleccionado
		   
		   AlertDialog.Builder builder = new AlertDialog.Builder(Cliente_Bluetooth.this);
	        builder.setMessage("�Est� seguro que desea eliminar el archivo?")
	        
	               .setPositiveButton("Eliminar", new DialogInterface.OnClickListener() {
	                   public void onClick(DialogInterface dialog, int id) {

	                	   //Si presiona el bot�n Eliminar, el archivo se elimina y se actualiza el contenido del Directorio Local
	                	   File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/fromSdCard/"+NombreAux);
	            		   boolean deleted = file.delete();
	            		   
	            		   MostrarContenidoDP();
	            		   
	                   }
	               }) 
	               .setNegativeButton("Cancelar", new DialogInterface.OnClickListener() {
	                   public void onClick(DialogInterface dialog, int id) {
	                       
	                	   //Si el usuario cancela la operaci�n, se cierra el AlertDialog
	                	   dialog.cancel();
	                   }
	               });
		   
	        builder.show();  
		   
	   }
	   
	   
	   /**
	    * Este m�todo inicializar� el hilo que mostrar� al Usuario el estado en el que se encuentra la
	    * descarga del archivo solicitado al Servidor
	    * @param indice: El indice del elemento seleccionado dentro del ArrayList
	    */
	   
	   
	   private void ActivarHilo(int Indice)
	   {
		   
		   LongitudArchivoSeleccionado = ArrayLongitudes[Indice];
		   
		   EnviarMensaje(MensajeParaServidor);
           
           //Si es el primer archivo que se descarga, se crea el hilo
           // en caso contrario, simplemente se reinician sus variables
           if (mHiloDescarga == null) {
	        	        	   
        	   	mHiloDescarga = new HiloDescarga();	        	  
        	   	mHiloDescarga.RestartThread();		         
        	   	mHiloDescarga.start();	         
	        	  
		    }else{
		    	
		    	mHiloDescarga.RestartThread();
		    	
		    }	   
		   
	   }
	   
	   /**
	    * M�todo que actualiza en la ventana superior el contenido del Directorio del Servidor
	    * en el que se encuentra el Usuario.
	    */
	
	   private void MostrarContenidoDispositivoRemoto(){

		   	//Definici�n de variables empleadas
		    int ComienzoExtension;
		    
		    Integer[] IdentificadorIconos = new Integer[IndiceDirectorio];	
			String[] NombresAuxiliar = new String[IndiceDirectorio];
			Integer[] Tama�osAuxiliar = new Integer[IndiceDirectorio];
			
			//Se introducen en un nuevo Array los nombres y los tama�os de lo elementos del Directorio
			//en el que se encuentra el usuario. Y se decide para cada elemento si se trata de un 
			//archivo o de un directorio anidado, para asignarle el icono correspondiente
			
			for(int i=0; i<IndiceDirectorio; i++)
			{
				NombresAuxiliar[i] = ArrayNombres[i+1];
				Tama�osAuxiliar[i] = ArrayLongitudes[i+1];
				
				ComienzoExtension = ArrayNombres[i+1].indexOf(".");
				
				if(ComienzoExtension<1)
					IdentificadorIconos[i]=R.drawable.icon_folder;
				else
				{
	
					if(ArrayNombres[i+1].contains(".pdf") | ArrayNombres[i+1].contains(".PDF"))
						IdentificadorIconos[i]=R.drawable.icon_pdf;
					else
						IdentificadorIconos[i]=R.drawable.icon_file;
				}
				
					
			}
			
			//A continuaci�n se pasan los nuevos arrays creados al CustomListAdapter
			CustomListAdapter adapter=new CustomListAdapter(this,  NombresAuxiliar, Tama�osAuxiliar, IdentificadorIconos);
			
			setListAdapter(adapter);
			
			getListView().setOnItemClickListener(mArchivosRemotosClickListener);
		
	
	   }
	   
	   /**
	    * M�todo que muestra el contenido del Directorio Local en la ventana inferior de la Aplicaci�n de Usuario
	    * 
	    */
	   
	   private void MostrarContenidoDP()
		{
		   
		   if(mHiloDescarga != null)
			   mHiloDescarga = null;
		   
		   //Borramos el contenido de la ventana inferior
		   mListaArchivosDPAdaptador.clear();
		   mListaArchivosDPAdaptador.notifyDataSetChanged();
	        	
	        //Sacamos el contenido del Directorio Local
	        File sdCardRoot = Environment.getExternalStorageDirectory();
	        File DirectorioLocal = new File(sdCardRoot, "/fromSdCard");     

	        for (File Archivo : DirectorioLocal.listFiles()) {
	     		
	           if (Archivo.isFile())
	            {    
	            	//Se a�ade el nombre de los diferentes Archivos al adaptador de la ventana inferior
	            	mListaArchivosDPAdaptador.add(Archivo.getName());

	            }
	    
	        }
			
		}	
	
	/**
	 * M�todo que detecta si existe alguna letra min�scula en el nombre de un archivo
	 * 
	 * @param nombre: Nombre del archivo
	 * @return true: Si existe alguna letra min�scula
	 *        false: Si el nombre est� formado exclusivamente por letras may�sculas
	 */
	private boolean MayusculasMinusculas(String nombre){
		
		for(int i=0; i< nombre.length() ; i++)
		{
			if(Character.isUpperCase(nombre.charAt(i)) == false)
				return true;
		}
		
		return false;
	}
	
	
	/**
	 * Metodo que env�a mensajes al Servidor haciendo uso del BluetoothService
	 * @param message: mensaje a enviar al Servidor
	 * 
	 */
	 private void EnviarMensaje(String Mensaje) {
		 
        // Se comprueba que el dispositivo est� conectado al Servidor
        if (mBluetoothService.getState() != BluetoothService.ESTADO_CONECTADO) {
        
            Toast.makeText(this, "No est�s conectado", Toast.LENGTH_SHORT).show();        
            return;
        }
        

        // Se comprueba que el par�metro mensaje no est� vac�o
        if (Mensaje.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] ComandoBytes = Mensaje.getBytes();
            mBluetoothService.EnviarComando(ComandoBytes);

        }
	 }
	 
	 /**
	  * Este hilo ser� el encargado de actualizar el ProgressAlert Descargando 
	  * que informar� al usuario sobre el estado de la descarga de archivos
	  */

   private class HiloDescarga extends Thread {
        
	   //Definici�n de variables
	   Boolean NombreModificado = false;	   
	   private boolean ThreadWorking = false;  
	   
	   float Progreso, AuxDescargados, AuxTotal;
	   int DelCont = 0;
	   
	   
	   //Variables para calcular la velocidad de transmisi�n y el tiempo restante
	   int VelocidadMedia, AuxVelocidadMedia, SegundosTranscurridos;
	   float AuxSize, AuxContador, AuxVelocidad, TiempoRestante, SegundosTransmision;
	   String StringAuxSize, StringAuxContador, StringAuxVelocidad, StringTiempoRestante;
	   
	   
	   // En el constructor se crea un nuevo ProgressDialog llamado DescargandoPD
        public HiloDescarga() {
            
        	DescargandoPD = new ProgressDialog(Cliente_Bluetooth.this);       
         
        }
        
        // Este m�todo permite al usuario cancelar la descarga de un archivo en cualquier momento
        // Las acciones a tomar al cancelar una transferencia son:
        //  -Eliminar el archivo del Directorio Local en el que se estaba almacenando la informaci�n recibida
        //  -Informar al Servidor que se ha cancelado la transferencia, para que cese el env�o de datos
        
        public void CancelarTransferencia()
        {
        	// Se detiene el hilo
        	ThreadWorking= false;               	
        	NombreModificado=false;
        	
        	// Se elimina el archivo creado en el Directorio Local
        	File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/fromSdCard/"+NombreArchivo);
    		boolean deleted = file.delete();
    		
    		// Se envia el mensaje de cancelaci�n al Servidor
    		EnviarMensaje("ccc000");
    		
    		// Se actualiza el contenido de la ventana inferior (Contenido del Directorio Local)
    		MostrarContenidoDP();
    		
    		// Se cierra el ProgresDialog
    		DescargandoPD.dismiss();
        	
        }
        
        // Este m�todo se emplea para actualizar las variables del hilo cada vez que se reciba
        // un nuevo archivo desde el Servidor
        public void RestartThread()
        {
        	
        	// Se reinician variables
        	ThreadWorking = true;
        	Progreso = 0;
        	
        	ContadorBytesSegundo = 0;
			VelocidadMedia = 0;
			SegundosTranscurridos=0;

			DelCont = 0;
			
			ContadorBytesTotales=0;
	        
			// El mensaje a mostrar cambiar� en funci�n de si fue necesario o no renombrar el archivo
			// por existir ya uno llamado igual en el Directorio Local
	        if(NombreModificado)
	        	DescargandoPD.setTitle("Recibiendo: "+NombreViejo+"\nRenombrado: "+NombreArchivo);
	        else
	        	DescargandoPD.setTitle("Recibiendo el archivo "+NombreArchivo);
	        
	        // Se inicaliza el ProgressDialog que mostrar� el estado de la descarga
	        DescargandoPD.setMessage("Recibiendo Archivo");
	        DescargandoPD.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
	        DescargandoPD.setIndeterminate(false);
	        DescargandoPD.setProgress(0);
	        DescargandoPD.setMax(100);
            
            // Se declara el bot�n de cancelaci�n de transferencia
	        DescargandoPD.setCancelable(false);
	        DescargandoPD.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancelar Transferencia", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                	CancelarTransferencia();
                    dialog.dismiss();
                    
                }
            });
            
	        DescargandoPD.show();
        }

        public void run() {	            
        	
        	
        	while(ThreadWorking){	        		
        			
        		
        		try {
        			
        			 // Cada 100 milisegundos se actualiza el n�mero de bytes recibidos
                     Thread.sleep(100);
                     DelCont++;   
                     
                     // Handler para actualizar la barra de estado, que representa el porcentaje de bytes recibidos
                     mBarraProgreso.post(new Runnable() {
                    	
                    	 public void run() {
                    	 	 
                    		// Cada segundo se analiza la velocidad media de descarga
                    		 if((DelCont>0) && (DelCont%10 ==0))
                    		 {	 

                    			 	if(SegundosTranscurridos>0)
                    			 		AuxVelocidadMedia = VelocidadMedia * SegundosTranscurridos;
                    			 	
                    			 	AuxVelocidadMedia += ContadorBytesSegundo;
                    			 	
                    			 	SegundosTranscurridos++;
                    			 	
                    			 	VelocidadMedia = AuxVelocidadMedia / SegundosTranscurridos;
                    			 	
                    			 	ContadorBytesSegundo = 0;	                 						 
                    		 
                    		 }
                    			 
                    		 
                    		 // Los datos anteriormente calculados se convierten en floats
                    		 AuxSize = (float)(LongitudArchivoSeleccionado/1000.0);
                    		 AuxContador =  (float)(ContadorBytesTotales/1000.0);
                    		 AuxVelocidad = (float)(VelocidadMedia/1000.0);
                    		 
                    		 TiempoRestante = (AuxSize - AuxContador) / AuxVelocidad;
                    		 
                    		 // Se formatean los valores de las variables anteriores para mostrar tan solo dos decimales
                    		 StringAuxSize = String.format("%.2f", AuxSize);
                    		 StringAuxContador = String.format("%.2f", AuxContador);
                    		 StringAuxVelocidad = String.format("%.2f", AuxVelocidad);
                    		 StringTiempoRestante = String.format("%.2f", TiempoRestante);
                    		 
                    		 // Se actualiza el mensaje con los bytes transmitidos, velocidad media y tiempo restante aproximado
                    		 DescargandoPD.setMessage("Recibidos: "+StringAuxContador+" Kbytes de "+StringAuxSize+" Kbytes @: "+StringAuxVelocidad+" KBps\nTiempo restante estimado: "+StringTiempoRestante+" Segundos");       		
                    		 
                    		 //Se actualiza el porcentaje de bytes recibidos
                    		 AuxDescargados = ContadorBytesTotales;
                    		 AuxTotal = LongitudArchivoSeleccionado;
                    		 Progreso = (AuxDescargados / AuxTotal)*100;
                    		 
                    		 DescargandoPD.setProgress((int)Progreso);
                    		 
                    		 // Una vez que el numero de bytes recibidos es igual al tama�o del archivo, se da por completada
                    		 // la transferencia. Se muestra AlertDialog para informar al usuario con el tiempo transcurrido y
                    		 // se cierra el ProgressDialog
                    		 
                    		 if((ContadorBytesTotales >= LongitudArchivoSeleccionado) & ThreadWorking)
                    		 {
                    			 	                    			 
                    			 SegundosTransmision = (float)DelCont/10;

                    			 NombreModificado = false;
                    			 
                    			  new AlertDialog.Builder(Contexto)
                    			    .setTitle("Se ha recibido correctamente el archivo "+NombreArchivo)
                    			    .setMessage("Tiempo transcurrido: "+SegundosTransmision+" segundos")
                    			    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    			        public void onClick(DialogInterface dialog, int which) { 
                    			           
                    			        }
                    			     })	                    			  
                    			     .show();
                    			 
                    			 
                    			 
                    			 ThreadWorking= false;
                    			 DescargandoPD.dismiss();
                    			 MostrarContenidoDP();
                    		 }
                    	 	 
                    	 }
                    	 	 
                    });
                     
                     
                 } catch (Exception e) {
                     
                 }
        		
        		}

	        }
	        
	        
	   }
   
   	   /**
   	    * Este m�todo almacena en dos arrays diferentes, pero que comparten �ndice, el nombre y la longitud de los elementos
   	    * almacenado en el directorio del Servidor en el que se encuentra el Usuario
   	    * 
   	    */
	   
   			private void AlmacenarElemento(String Informacion)
   			{
   				String[] NombreLongitud = Informacion.split("-");
                
                if((NombreLongitud[0].length() ==1) && (NombreLongitud[0].contains(".")))
                	ContadorDirectoriosInvisibles++;
                else
                {
                	IndiceDirectorio++;
                	
	                ArrayNombres[IndiceDirectorio]= NombreLongitud[0];	               
	                
	                Integer Longitud = Integer.parseInt(NombreLongitud[1]);
	                ArrayLongitudes[IndiceDirectorio]=Longitud;
                }
   				
   			}
	   
	   /**
		 * El Handler se ocupara de recibir y procesar los mensajes enviados del Servicio BluetoothService
		 * a la Activity Cliente_Bluetooth
		 */
		    private final Handler mHandler = new Handler() {
		    	
		        @Override
		        public void handleMessage(Message msg) {
		            switch (msg.what) {
		            
		            //Se deja listo para futuras ampliaciones
		            case MENSAJE_CAMBIO_ESTADO:
		            	    
		                switch (msg.arg1) {
		                
			                case BluetoothService.ESTADO_CONECTADO:
			                    
			                	mListaArchivosRemotosAdaptador.clear();			                    
	
		                }
		                
		                break;		                
		            	
		            case MENSAJE_BYTES_RECIBIDOS:
		            	

		            	ContadorBytesTotales += msg.arg1;
		            	
		            	ContadorBytesSegundo += msg.arg1;
		            	
		            	break;
		                
		            case MENSAJE_ELEMENTO:
		            	
		                byte[] readBuf = (byte[]) msg.obj;
		 
		                String InformacionRecibida = new String(readBuf, 0, msg.arg1);
		                
		                
		                AlmacenarElemento(InformacionRecibida);
		                

		                break;
		                

		            case MENSAJE_INDICE:
		            	
		            		MostrarContenidoDispositivoRemoto();
		            		
		            		BotonEnviar.setText("Actualizar Directorio Local");
		            		FlagDirectorioArchivo = true;
		            		//System.out.println("Se ha llegado al final del fichero");
		            		//CounterBytes=CurrentSize;
		            	
		            	break;
		                
		            case MENSAJE_CONECTADO:
		            	
		            	String mConnectedDeviceName = null;
		            	
		            	mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
		            	
		            	mConectandoPD.dismiss();
		            	
		                Toast.makeText(getApplicationContext(), "Conectado a "
		                		 + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
		                
		                System.out.println("Se efectuar�a la conexion");
		                ConexionEstablecida();
		                
		                break;
		                
		            case MENSAJE_SALUDO:
		            	
		                Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
		                               Toast.LENGTH_SHORT).show();
		                
		                break;
		                
		            }
		        }
		    };
	 
}



