/////////////////////////////////////////////////////////////////////////////
////                     main.c			                                 ////
////                                                                     ////
////																     ////
//// Autor: Ignacio Alonso Larré										 ////
////                                                                     ////
////			                                                         ////
//// Descripción: Es el programa pincipal del Proyecto. Se encarga de    ////
//// inicializar primeramente el microcontrolador, para entrar a         ////
//// continuación en un bucle infinito en el que escucha peticiones de   ////
//// los usurios, y les envía a través de Bluetooth los elementos        ////
//// correspondientes, ya sean indices de ficheros o archivos.			 ////										            
////                                                                     ////
/////////////////////////////////////////////////////////////////////////////

#include <18F27J13.h>
#device HIGH_INTS = TRUE
#device PASS_STRINGS = IN_RAM
#fuses NOWDT, HS
#use delay(clock=20000000)

#use RS232(UART, baud = 115200, xmit = PIN_C6 ,rcv = PIN_C7, PARITY = N, bits = 8, stop = 1, stream = BLUETOOTH)

#use fast_io(A)
#use fast_io(B)
#use fast_io(C)

#include <stdlib.h> 
#include <stdio.h>
#include <STDLIBM.H>

#byte TRISA=0xF92
#byte TRISB=0xF93
#byte TRISC=0xF94

//Driver del LCD
#include "lcd_ATE4B.c"

//Asignación de pines para el manejo de la MMC
#define MMCSD_PIN_SCL     PIN_B2 //o
#define MMCSD_PIN_SDI     PIN_B1 //i
#define MMCSD_PIN_SDO     PIN_B0 //o
#define MMCSD_PIN_SELECT  PIN_B3 //o

//Driver de la MMC
#include "mmcsd.c"

//Librería fat
#include "fat.c"

//Definición de bits para comprobar la presencia de la MMC
#byte PORTC = 0XF82
#bit HAY_SD_SALIDA = PORTC.0
#bit HAY_SD_ENTRADA = PORTC.1

//Asignación del pin del LED
#byte PORTA = 0XF80
#bit LED = PORTA.3

//Flags
int NUEVO_COMANDO = 0;


//Estructura para almacenar los elementos de cada directorio
typedef struct {

	int Indice;
	int Tipo;
	int16 PrimerCluster;
	int32 LongitudElemento;

}ListaDirectorio;

ListaDirectorio Ld[127];

//Buffer para almacenar los bytes enviados desde el Usuario
char BufferCaracteresBT[7];
int IndiceCaracteresBT;

///////////////////////DEsde aqui hay que seguir comentando y cambiando nombres

//#define COMMAND_SIZE 10
///#define NUM_COMMANDS 11





int32 DireccionRoot;
int32 DireccionDirectorioActual;
int32 DireccionUltimoDirectorio;
//int16 BytesPorCluster;

int32 RutaDirectoriosAnidados[12];
int IndexDirectoriosAnidados = 0;



//La interrupción ligada al módulo UART ejecutará el método bt()
#INT_RDA HIGH

//////////////////////////////////////////////////////////////////////////////////////////////////////
//
// EscuchaBluetooth(): Se encarga de almacenar en un buffer los bytes recibidos a través del 
//                     stream BLUETOOTH. Una vez se hayan recibido 6 bytes (caracteres), el Servidor 
//			           ya dispone de información suficiente para analizar el comando y llevar a  
//                     cabo la acción pertinente. En ese momento se activa el flag NUEVO_COMANDO,
//			           el cual se revisa periódicamente dentro del bucle principal.
//
//////////////////////////////////////////////////////////////////////////////////////////////////////

void EscuchaBluetooth()
{      
	BufferCaracteresBT[IndiceCaracteresBT]=fgetc(BLUETOOTH);
	IndiceCaracteresBT++;
	
	if(IndiceCaracteresBT==6)
	{
		NUEVO_COMANDO=1;	
	}
}

////////////////////////////////////////////////////////////////////////////////////////////////////
//
// VaciarBuffer(): Resetea el buffer, para dejarlo listo ante la llegada de nuevos comandos 
//				  provenientes del Usuario.
//
////////////////////////////////////////////////////////////////////////////////////////////////////

void VaciarBuffer()
{
	for(int IndiceBorrado=0; IndiceBorrado<7; IndiceBorrado++)
	{
		BufferCaracteresBT[IndiceBorrado]=0;

	}
	IndiceCaracteresBT=0;
}

////////////////////////////////////////////////////////////////////////////////////////////////////
//
// InicializacionMMC(): Este método inicializa la FAT, y extre información sobre su formato
//                      necesaria para las acciones de lectura. Debe de ser invocado cada vez
//					    que se escriba algo en el LCD, ya que al compartir su mismo puerto puede
//					    provocar algún tipo de desconfiguración.
//
////////////////////////////////////////////////////////////////////////////////////////////////////

void InicializacionMMC()
{

	InicializacionFAT(&DireccionRoot/*, &BytesPorCluster*/);

}

////////////////////////////////////////////////////////////////////////////////////////////////////
//
// MensajesLCD(i): Se encarga de mostrar al usuario un mensaje a través del LCD en función del código
//				   recibido. Este mensaje servirá para informar sobre el proceso que se esté llevando
//                 a cabo o para solicitarle al Usuario que realice algúna acción concreta.
//				   En este método se usan las siguientes funciones del driver del LCD:
//
//                 		-lcd_putc("\f"): Borra el contenido acutal del display.
//						-lcd_gotoxt(1,1): Desplaza el cursor del LCD al inicio.
//						-lcd_putc("text"): Muestra el string "texto" en el display
//
////////////////////////////////////////////////////////////////////////////////////////////////////

void MensajesLCD(int CodigoMensaje)
{
	lcd_putc("\f");
	lcd_gotoxy(1,1);
	
	switch(CodigoMensaje)
	{

		case 1:				
				lcd_putc("  Esperando \n  Usuarios...");			
		break;

		case 2:				
				lcd_putc("Indice Enviado");		
		break;
	
		case 3:				
				lcd_putc("Enviando Archivo");
		break;

		case 4:				
				lcd_putc(" Archivo Enviado");			
		break;

		case 5:				
				lcd_putc("Esperando Acción\n del Usuario");
		break;

		case 6:				
				lcd_putc("   Transmisión \n   Cancelada");
		break;
		
		case 7:				
				lcd_putc(" El Usuario se\n ha Desconectado");
		break;

		case 8:				
				lcd_putc(" La MMC no ha \n sido detectada");
		break;

		case 9:				
				lcd_putc("El formato de la\nMMC no es válido");
		break;

		case 10:				
				lcd_putc("   Introduzca \n    una MMC");
		break;

		case 11:				
				lcd_putc("   Introduzca \n    otra MMC");
		break;

	}

	InicializacionMMC();

}

//////////////////////////////////////////////////////////////////////////////////////
//
//	FinTransmisionIndice(), FinTransmisionArchivo(), Despedida(), Cancelacion(),
//  SinMMC(), FormatoMMCerroneo(): Son una serie de métodos que invocarán a 
//  MensajesLCD() enviandole el código correspondiente y en algunos casos, haciendo
//  una pequeña pausa entre mensajes
//
//////////////////////////////////////////////////////////////////////////////////////

void FinTransmisionIndice()
{	
	MensajesLCD(5);
}	

void FinTransmisionArchivo()
{
	MensajesLCD(4);	
	delay_ms(1000);
	MensajesLCD(5);
}

void Despedida()
{
	MensajesLCD(7);
	delay_ms(2500);
	MensajesLCD(1);
}

void Cancelacion()
{
	MensajesLCD(6);
	delay_ms(1500);
	MensajesLCD(5);
}

void SinMMC()
{
	MensajesLCD(8);
	delay_ms(1200);
	MensajesLCD(10);

}

void FormatoMMCerroneo()
{
	MensajesLCD(9);
	delay_ms(1500);
	MensajesLCD(11);
}

//////////////////////////////////////////////////////////////////////////////////////
//
//  ConvertirCodigo(): Este método convierte los tres últimos caracteres del comando
//                     gflXXX en un numero entero. Por tanto tan solo podría haber
//                     127 elementos en cada nivel de la MMC. 
//
//////////////////////////////////////////////////////////////////////////////////////

int ConvertirCodigo() //Esta limitado a 127, la capacidad de un int.
{
	int numero;
	int IndiceDirectorio;
	IndiceDirectorio = 0;

	numero = (int)BufferCaracteresBT[3];
	numero -= 48;
	numero = numero * 100;
	
	
	IndiceDirectorio+=numero;


	numero = (int)BufferCaracteresBT[4];
	numero -= 48;
	numero *=10;

	IndiceDirectorio+=numero;

	numero = (int)BufferCaracteresBT[5];
	numero -= 48;

	IndiceDirectorio+=numero;
		
	return IndiceDirectorio;
}

///////////////////////////////////////////////////////////////////////////////////////////////
//
// EnviarContenidoDirectorio(d,a): Esta función se encarga de enviar al usuario el contenido 
//                                 del directorio al que se acabe de desplazar. En caso de
//                                 estar inicializando el Servidor, se enviará el contenido del
//                                 Directorio Raíz.
//
///////////////////////////////////////////////////////////////////////////////////////////////

void EnviarContenidoDirectorio(int32 DireccionInicioDirectorio, int Avance)
{

	if(Avance)
		RutaDirectoriosAnidados[IndexDirectoriosAnidados++] = DireccionInicioDirectorio;

	int TipoElementoMMC;
	int IndiceDirectorioMMC=0;
	int IndexListaDirectorio = 1;
	int IndiceNombreLargo;

	char NombreBuffer[32];


	while(TRUE){


		Ld[IndexListaDirectorio].Indice = IndexListaDirectorio;
	
		TipoElementoMMC=SacarInfo(DireccionInicioDirectorio, IndiceDirectorioMMC++, &NombreBuffer, &Ld[IndexListaDirectorio].PrimerCluster, &Ld[IndexListaDirectorio].LongitudElemento, &Ld[IndexListaDirectorio].Tipo);
		int32 LongitudArchivo =	Ld[IndexListaDirectorio].LongitudElemento;
	

		if(TipoElementoMMC == 0xE5) //Representa a un archivo borrado
			continue;

		if(TipoElementoMMC == 0x00)
		{
			fprintf(BLUETOOTH, "%c",0x07);

			delay_ms(200);

			break;
		}

		if((TipoElementoMMC == 0x20) || (TipoElementoMMC == 0x10))//Es un archivo con nombre corto
		{
			
			if(TipoElementoMMC == 0x20)
			{
				for(int w=0; w<11; w++)
				{
					if(NombreBuffer[w]!=0x20)//Quitamos los espacios en blanco					
						putc(NombreBuffer[w]);
					
					if(w==7)
						fprintf(BLUETOOTH, "%c",0x2E); //Añadimos el "."								.						
				}
			}else{

				for(int w=0; w<11; w++)
				{
					if(NombreBuffer[w]!=0x20)//Quitamos los espacios en blanco
						putc(NombreBuffer[w]);//final_name[indexName++] = buffer_name[w];						.						
				}
			}

			fprintf(BLUETOOTH, "%c", 0x2D);
			fprintf(BLUETOOTH,"%lu",LongitudArchivo);
			fprintf(BLUETOOTH, "%c",0x04);
			
			IndexListaDirectorio++;

			delay_ms(100);

		}else{ //Es un elemento con nombre largo
			
			IndiceNombreLargo=0;

			while(TRUE)
			{
				if(NombreBuffer[IndiceNombreLargo] == 0x00 | IndiceNombreLargo>128)
					break;

				putc(NombreBuffer[IndiceNombreLargo]);
				NombreBuffer[IndiceNombreLargo] = 0x00;//Limpiamos el array para la proxima vez
				IndiceNombreLargo++;
			}
			
			fprintf(BLUETOOTH, "%c", 0x2D);
			fprintf(BLUETOOTH,"%lu",LongitudArchivo);
			fprintf(BLUETOOTH, "%c",0x04);

			IndexListaDirectorio++;

			delay_ms(100);
			//Cuando se trata de un elemento con nombre largo, el valor de TipoElementoMMC
			//nos indicará cuántos entradas del directorio hay que saltar para pasar al siguiente
			//elemento 
			IndiceDirectorioMMC += TipoElementoMMC;

		}
	
	}

	FinTransmisionIndice();

}

//////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// EnviarArchivo(): Este método se encarga de enviar al usuario el Archivo seleccionado. En el caso
//                  de que el usuario haya seleccionado un Directorio en vez de un archivo, se almacenará
//					la dirección de memoria del dirctorio actual, y se hará una llama a 
//					EnviarContenidoDirectorio() pasando como parámetro la dirección del nuevo Directorio
//					que se desea cargar.                              
//
//////////////////////////////////////////////////////////////////////////////////////////////////////////

void EnviarArchivo()
{	
	int IndiceDirectorio = ConvertirCodigo();
	VaciarBuffer();
	int TransmisionCancelada = 1;

	char BufferDatosLeidos[512];

	int TipoElemento = Ld[IndiceDirectorio].Tipo;

	if(TipoElemento == 0x01) //Es un directorio
	{
		int16 Cluster = Ld[IndiceDirectorio].PrimerCluster;
		int32 DIR_CURRENT_CLUSTER = ROOT_POSITION+((Cluster-2)*BYTES_PER_CLUSTER);
		DireccionUltimoDirectorio = DireccionDirectorioActual;
		EnviarContenidoDirectorio(DIR_CURRENT_CLUSTER, 1);

	}else{ //Es un archivo

		int16 indexData;//=0;

		//int16 NumeroSectores;
		//int32 RestoCluster;
		int16 BytesLeidos;
		 

		int16 LongitudSector = 512;
	
		int16 PrimerClusterArchivo = Ld[IndiceDirectorio].PrimerCluster;
		int32 LongitudArchivo = Ld[IndiceDirectorio].LongitudElemento;
		
		int16 NumeroBytesUltimoSector = LongitudArchivo % LongitudSector; 

		int16 NumeroSectores = LongitudArchivo / LongitudSector;
		int16 UltimoSector = NumeroSectores - 1;

		NumeroSectores++;
	
		MensajesLCD(3);

		for(int16 ContadorSectores = 0; ContadorSectores < NumeroSectores; ContadorSectores++)
		{
			indexData=0;
	
			//En caso de que el Usuario envíe el comando can
			//se dentendrá la transmisión del Archivo
			if(NUEVO_COMANDO)
			{
				NUEVO_COMANDO = 0;

				if(strncmp(BufferCaracteresBT,"can",1)==0)
				{
					VaciarBuffer();
					ContadorSectores = NumeroSectores+1;
				}
				
				TransmisionCancelada = 0;
				Cancelacion();
				
			}
	
			//Con esto el LED parpadea durante la transmisión
			if(LED)
				LED = 0;
			else
				LED = 1;


	
			if(ContadorSectores > UltimoSector)
			{		
				BytesLeidos = NumeroBytesUltimoSector;
			
				if(NumeroBytesUltimoSector>0) 
					GetFileContent(PrimerClusterArchivo, BytesLeidos, ContadorSectores, &BufferDatosLeidos);
			}
			else
			{
				BytesLeidos = LongitudSector; 
	
				GetFileContent(PrimerClusterArchivo, BytesLeidos, ContadorSectores, &BufferDatosLeidos);
			}
	
			for(int16 ContadorBytesEnviados = 0; ContadorBytesEnviados < BytesLeidos; ContadorBytesEnviados++)
				fputc(BufferDatosLeidos[ContadorBytesEnviados]);		
		}

		delay_ms(300);

		if(TransmisionCancelada)
		{
			FinTransmisionArchivo();
	
		}

	}

	LED = 0;
	
}


/////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// main(): Se trata de la función principal. Se encarga de inicializar una serie de registros del micronctrolador
//         y una serie de drivers, que permiten el correcto funcionamiento del Servidor. Una vez que se ha
//		   llevado a cabo el proceso de inicialización, el Servidor entra en un bucle infinito a la espera de
//         comandos enviados por el usuario, que determinarán las operaciones que se llevarán a cabo.
// 
////////////////////////////////////////////////////////////////////////////////////////////////////////////////

void main(void)
{

	//Se definen los valores de los TRIS
	TRISA=0b00000000;   // X,X,X,X, LED,LCD_E, LCD_RW, LCD_RS 
	TRISB=0b00000010;  //PGD & LCD_D7 , PGC & LCD_D6 , LCD_D5 , LCD_D4 , CS,SCLK,MOSI,MOSO
    TRISC=0b10000010;

   // Se activan interupciones
   enable_interrupts(INT_RDA);  
   enable_interrupts(GLOBAL);
	
	//Se inicializa el LCD
	lcd_init();
	
	//Se apaga el LED
	LED = 0;

	//Se vacía el buffer que espera por comandos
	VaciarBuffer();


	//Se comprueba si la MMC cumple los requisitos para funcionar correctamente

	//1- Primeramente se revisa si se ha introducido una memoria SD
	
	HAY_SD_ENTRADA = 0;
	HAY_SD_SALIDA = 1;

	int PuedeFuncionar = 0;

	if(HAY_SD_ENTRADA)
	{

		HAY_SD_SALIDA = 0;

		InicializacionMMC();

		//2- Se comprueba si la tarjeta es o no compatible
		if(DireccionRoot>10000000)//La tarjeta no es compatible y el valor de DireccionRoot es erróneo
		{
			FormatoMMCerroneo();
			PuedeFuncionar = 0;

		}else{
			
			PuedeFuncionar = 1;

		}

	}
	else
	{
		HAY_SD_SALIDA = 0;
		SinMMC();
		PuedeFuncionar = 0;
	}


	if(PuedeFuncionar)
	{
		MensajesLCD(1);
	
		DireccionDirectorioActual = DireccionRoot;
		RutaDirectoriosAnidados[IndexDirectoriosAnidados++] = DireccionDirectorioActual;	
	
		while(TRUE)	
		{
	
			if(NUEVO_COMANDO)
			{

				NUEVO_COMANDO = 0;
				
	
				if(strncmp(BufferCaracteresBT,"gfl",3)==0) 
				{				
					EnviarArchivo();					
				}
	
	
				if(strncmp(BufferCaracteresBT,"fat",3)==0)
				{
					VaciarBuffer();
					EnviarContenidoDirectorio(DireccionRoot,1);		
				}
	
				if(strncmp(BufferCaracteresBT,"bkd",3)==0)
				{
					VaciarBuffer();
					IndexDirectoriosAnidados--;
					EnviarContenidoDirectorio(RutaDirectoriosAnidados[IndexDirectoriosAnidados-1],0);		
				}
	
				if(strncmp(BufferCaracteresBT,"adi",3)==0)
				{
					VaciarBuffer();
					Despedida();		
				}						
			}	
		}
	}
}
	