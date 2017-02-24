
#ifndef FAT_PIC_C
#define FAT_PIC_C

#include <ctype.h>
#include <string.h>
#case

//////////////////////
///                ///
/// Useful Defines ///
///                ///
//////////////////////

/// Define your FAT type here ///
//#define FAT16
#define FAT32

/// For faster single-file writing, uncomment this line below ///
//#define FAST_FAT

/// Everything else ///
#define MAX_FILE_NAME_LENGTH 0x20  // the maximum length of a file name for our FAT, including /0 terminator
#define STREAM_BUF_SIZE 0x20       // how big the FILE buffer is. 0x20 is optimal

//////////////////////////////////////////////////////////////////

#define EOF -1
#define GOODEC 0
#define fatpos_t int32
#define SEEK_CUR 0
#define SEEK_END 1
#define SEEK_SET 2

////////////////////////
///                  ///
/// Global Variables ///
///                  ///
////////////////////////

int16
   Bytes_Per_Cluster,   // number of addressable bytes per cluster
   FAT_Start;           // when the first FAT begins

int32
   Data_Start,          // when data starts
   FAT_Length,          // the length of one FAT
   Next_Free_Clust,     // where the next free cluster is
   ROOT_POSITION,
   FAT_POSITION,
   BYTES_PER_CLUSTER,
   Root_Dir;            // when the root directory starts

enum filetype
{
   Data_File,  // the stream is pointing to a binary, data file
   Directory,  // the stream is pointing to a directory
   None        // the stream isn't currently pointing to anything
};

enum ioflags
{
   Closed = 0x00,
   Read = 0x01,
   Write = 0x02,
   Append = 0x04,
   Binary = 0x08,
   EOF_Reached = 0x10,
   Read_Error = 0x20,
   Write_Error = 0x40,
   File_Not_Found = 0x80
};

struct iobuf
{
   fatpos_t
      Bytes_Until_EOF,     // how many bytes until the stream's end of file
      Cur_Char,            // the current byte that the stream is pointing at
      Entry_Addr,          // the entry address of the file that is associated with the stream
      Parent_Start_Addr,   // the parent's start adddress of the file that is associated with the stream
      Size,                // the size of the file that is associated with the stream
      Start_Addr;          // the beginning of the data in the file that is associated with the stream

   enum filetype File_Type;   // the type of file that is associated with the stream

   enum ioflags Flags;        // any associated input/output flag

   int Buf[STREAM_BUF_SIZE];  // this is a buffer so that during fatputc() or fatgetc()
                              //  the media won't have to be read at every character
};
typedef struct iobuf FILE;

///////////////////////////
///                     ///
/// Function Prototypes ///
///                     ///
///////////////////////////




int SacarNombre();
signed int InicializacionFAT();


/// Data Utility Functions ///

/*
signed int fat_init()
Summary: Initializes global variables that are essential for this library working
Returns: EOF if there was a problem with the media, GOODEC if everything went okay.
Note: This must be called before any other function calls in this library.
*/
signed int InicializacionFAT(int32 *DireccionTablaRoot/*, int16 *BytesPerCluster*/)
{
   int ec = 0;

   int
      FATs,
      Sectors_Per_Cluster;
	  
   char file_name[8];

   int16
      Bytes_Per_Sector,
      Reserved_Sectors,  
      Small_Sectors,
	  Extension,
	  BOOT_OFFSET;
	  

   int32
      Hidden_Sectors,
	  BOOT_POSITION,
	  Name,
      Large_Sectors;

#ifdef FAT32
   int32 Sectors_Per_FAT;
#else // FAT16
   int16
      Root_Entries,
      Sectors_Per_FAT;
#endif // #ifdef FAT32

   // initialize the media
   ec += mmcsd_init();
   // start filling up variables
   BOOT_POSITION = 0x00000000;
   BOOT_OFFSET = 0x00000000;


   /*ec += mmcsd_read_data(0x1C6, 2, &BOOT_OFFSET);

   BOOT_POSITION = BOOT_OFFSET * 512;*/

   ec += mmcsd_read_data(BOOT_POSITION+11, 2, &Bytes_Per_Sector);
   ec += mmcsd_read_data(BOOT_POSITION+13, 1, &Sectors_Per_Cluster);
   ec += mmcsd_read_data(BOOT_POSITION+14, 2, &Reserved_Sectors);
   ec += mmcsd_read_data(BOOT_POSITION+16, 1, &FATs);

#ifdef FAT16
   ec += mmcsd_read_data(17, 2, &Root_Entries);
#endif // #ifdef FAT16
   ec += mmcsd_read_data(19, 2, &Small_Sectors);
#ifdef FAT32
   ec += mmcsd_read_data(BOOT_POSITION+36, 4, &Sectors_Per_FAT);
#else // FAT16
   ec += mmcsd_read_data(BOOT_POSITION+22, 2, &Sectors_Per_FAT);
#endif // #ifdef FAT32
   ec += mmcsd_read_data(28, 4, &Hidden_Sectors);
   ec += mmcsd_read_data(32, 4, &Large_Sectors);
#ifdef FAT16
   Next_Free_Clust = 2;
#else
   ec += mmcsd_read_data(0x3EC, 4, &Next_Free_Clust);
#endif
   if(ec != GOODEC)
      return EOF;

	ROOT_POSITION = ((FATs*Sectors_Per_FAT)+Reserved_Sectors+BOOT_OFFSET)*Bytes_Per_Sector;	
	*DireccionTablaRoot = ROOT_POSITION;
	 

	int32 ADD_RESERVED = 0x00000000;
	ADD_RESERVED += Bytes_Per_Sector;
	ADD_RESERVED *= Reserved_Sectors;
	FAT_POSITION = BOOT_POSITION + ADD_RESERVED;


   // figure out the size of a cluster
   BYTES_PER_CLUSTER = Sectors_Per_Cluster * Bytes_Per_Sector;
   //*BytesPerCluster = BYTES_PER_CLUSTER;
   // figure out how long one FAT is
   FAT_Length = Sectors_Per_FAT * (int32)Bytes_Per_Sector;

   // figure out where the FAT starts
   FAT_Start = Reserved_Sectors * Bytes_Per_Sector;

   // figure out where the root directory starts
   Root_Dir = FAT_Start + (FATs * FAT_Length);

   // figure out where data for files in the root directory starts
#ifdef FAT32
   Data_Start = Bytes_Per_Cluster + Root_Dir;
#else // FAT16
   Data_Start = (Root_Entries * 0x20) + (Bytes_Per_Sector - 1);
   Data_Start /= Bytes_Per_Sector;
   Data_Start += Reserved_Sectors + (FATs * Sectors_Per_FAT);
   Data_Start *= Bytes_Per_Sector;
#endif // #ifdef FAT32

   return GOODEC;
}

/*
signed int get_next_cluster(int16* my_cluster)
Summary: Gets the next linked cluster from the FAT.
Param: A pointer to a variable that holds a cluster.
        This variable will then have the next linked cluster when the function returns.
Returns: EOF if there was a problem with the media, GOODEC if everything went okay.
*/
#ifdef FAT32
signed int get_next_cluster(int32* my_cluster)
#else
signed int get_next_cluster(int16* my_cluster)
#endif
{
   // convert the current cluster into the address of where information about
   //  the cluster is stored in the FAT, and put this value into the current cluster
#ifdef FAT32
   if(mmcsd_read_data((*my_cluster << 2) + FAT_Start, 4, my_cluster) != GOODEC)
      return EOF;
#else // FAT16
   if(mmcsd_read_data((*my_cluster << 1) + FAT_Start, 2, my_cluster) != GOODEC)
      return EOF;
#endif // #ifdef FAT32
   return GOODEC;
}

/*
signed int get_prev_cluster(int32* my_cluster)
Summary: Gets the previously linked cluster in the FAT.
Param: A pointer to a variable that holds a cluster.
        This variable will then have the previous linked cluster when the function returns.
Returns: EOF if there was a problem with the media, GOODEC if everything went okay.
*/
#ifdef FAT32
signed int get_prev_cluster(int32* my_cluster)
#else
signed int get_prev_cluster(int16* my_cluster)
#endif // #ifdef FAT32
{
#ifdef FAT32
   int32
      cur_cluster = 1,
      target_cluster = 0;
#else
   int16
      cur_cluster = 1,
      target_cluster = 0;
#endif // #ifdef FAT32
   
   while(target_cluster != *my_cluster)
   {   
      cur_cluster += 1;
#ifdef FAT32
      if(mmcsd_read_data((cur_cluster << 2) + FAT_Start, 4, &target_cluster) != GOODEC)
         return EOF;
#else // FAT16
      if(mmcsd_read_data((cur_cluster << 1) + FAT_Start, 2, &target_cluster) != GOODEC)
         return EOF;
#endif // #ifdef FAT32
   }
   
#ifdef FAT32
   *my_cluster = cur_cluster;                        
#else // FAT16
   *my_cluster = cur_cluster;
#endif // #ifdef FAT32   
   
   return GOODEC;
}


/*
int GetNumFiles(int index)
{
	int State=0x00;
	int Begging = 0x00;
	int32 Offset;
	int32 constante = 0x00000020;
	int32 DIR_MEMORIA;

	Offset=(int32)(index*constante);
	
	DIR_MEMORIA = 	ROOT_POSITION+Offset;

	mmcsd_read_data(DIR_MEMORIA, 1, &Begging);
	
	if(Begging==0x00 | Begging==0xE5)
		return Begging;
	
	DIR_MEMORIA = ROOT_POSITION+Offset+11;

	mmcsd_read_data(DIR_MEMORIA, 1, &State);

	return State;

}*/

int GetFileContent(int16 Cluster, int16 NumeroBytesParaLeer, int32 modif, char *BufferDatos)
{
	int32 DIR_MEMORIA;
	int32 DIR_NEXT_CLUSTER;
	int32 DIR_CURRENT_CLUSTER;

	int16 BytesPorSector = 512;
	//int32 constante = 0x00000000;
	int32 modificadorMemoria = (int32) (BytesPorSector * modif);
	///////////////////////////Se recogen mal los datos de la estructura
	//Cluster = 22;//3;//22;//8;

	

	DIR_CURRENT_CLUSTER = ROOT_POSITION+((Cluster-2)*BYTES_PER_CLUSTER)+ (modificadorMemoria);//(constante*modif);//Sería a partir de donde se empiezan a contar, numero de clusters x tamaño de cada cluster

	DIR_MEMORIA = FAT_POSITION + (Cluster*4); 

	mmcsd_read_data(DIR_MEMORIA, 4, &DIR_NEXT_CLUSTER);

	mmcsd_read_data(DIR_CURRENT_CLUSTER, NumeroBytesParaLeer, BufferDatos); /*numBytes*/

	//Modificar esto para que devuelva el número del siguiente cluster, y asi meterlo en un bucle.
	//Dejar como opción que se envie el final del documento, para indicar que no hay que transmitir nada mas

	return 0;

}

///////////////Esto es lo nuevo
int SacarNombreLargo(int32 ComienzoMemoria, int loops, char *Nombre)
{
	int32 DIR_TAMANO_NOMBRE;
	int32 DIR_MEMORIA_NOMBRE_RECURSIVA;
	int32 RESTAR_CONSTANTE = 0X00000020;
	//int TamanoNombre;
	int16 indiceNombre = 0;
	int Caracter;
	
	//Bucle para los bloques de 32bytes
	for(int ContLoopsNL=1; ContLoopsNL<loops+1; ContLoopsNL++ )
	{
		//Nos posicionamos en el bloque correspondiente
		DIR_MEMORIA_NOMBRE_RECURSIVA = ComienzoMemoria - (RESTAR_CONSTANTE * ContLoopsNL);		

		for(int ContNL=0; ContNL<13; ContNL++ ) //Vamos leyendo los caracteres dentro de cada bloque de 32
		{
			if(ContNL==0)
				DIR_MEMORIA_NOMBRE_RECURSIVA += 0x01;
			else if(ContNL==5)
				DIR_MEMORIA_NOMBRE_RECURSIVA += 0x05;
			else if(ContNL==11)
				DIR_MEMORIA_NOMBRE_RECURSIVA += 0x04;
			else
				DIR_MEMORIA_NOMBRE_RECURSIVA += 0x02;

			mmcsd_read_data(DIR_MEMORIA_NOMBRE_RECURSIVA, 1, &Caracter);
			
			Nombre[indiceNombre++] = Caracter;

			//Comprobamos el final
			if(Caracter == 0x00)
			{
				ContLoopsNL = loops+1;
				break;
			}

			//Comprobamos el final
			/*if(indiceNombre>=TamanoNombre)
			{
				ContLoopsNL = loops+1;
				break;
			}*/

		}
			
	}

	//Aqui ya tendríamos el nombre largo almacenado en el array NombreLargo[]
	//Falta ver como lo devolvemos		

}


			
int SacarInfo(int32 DireccionInicio, int index, char *name,  int16 *Fcluster, int32 *SizeFile, int *Tipo)
{
	int State=0x00;
	int Begging = 0x00;
	int BeggMascara;
	int LoopsNL;
	int32 Offset;
	int32 constante = 0x00000020;
	int32 DIR_MEMORIA;
	int FlagNombreLargo = 0;

	Offset=(int32)(index*constante);

	// Primeramente se comprueba que no se ha alcanzado el final de la tabla ROOT
	DIR_MEMORIA = 	DireccionInicio+Offset;
	mmcsd_read_data(DIR_MEMORIA, 1, &Begging);
	
	if(Begging == 0x00) 
		return 0x00;
	else if(Begging == 0xE5)
		return 0xE5;


	//Comprobamos que tipo de elemento es el que estamos analizando

	DIR_MEMORIA += 0x0B;
	mmcsd_read_data(DIR_MEMORIA, 1, &State);	

	if(State == 0x0F)//Es un nombre largo
	{
		if(FlagNombreLargo == 0)
		{
			BeggMascara = 	Begging & 0xF0;		
			LoopsNL = Begging & 0x0F;

			if(BeggMascara == 0x40)//Es el comienzo de un nombre largo
			{
				FlagNombreLargo = 1;
			}
		}

	}

	if(FlagNombreLargo == 1) //Estamos ante un nombre largo	
	{
		FlagNombreLargo = 0;
		int Tamano;
		//char NombreLargo[127];

		//Lo primero es posicionarse donde está la información relevante del elemento de nombre largo
		Offset=(int32)(LoopsNL*constante);

		DIR_MEMORIA += Offset;
		mmcsd_read_data(DIR_MEMORIA, 1, &State);

		DIR_MEMORIA -= 0x0B;
		//SacarNombreLargo(DIR_MEMORIA, LoopsNL, &name);	
		mmcsd_read_long_name(DIR_MEMORIA, LoopsNL, name);

		DIR_MEMORIA += 0x1A;
		mmcsd_read_data(DIR_MEMORIA, 2, Fcluster);

		DIR_MEMORIA +=	0x02;	
		mmcsd_read_data(DIR_MEMORIA, 4, SizeFile);

			
		if(State == 0x10)
			*Tipo = 1;
		if(State == 0x20)
			*Tipo = 0;

		//Al ser un nombre largo se leyeron ya entradas de la root para formar el nombre final
		//Ese numero de entradas se le indica al programa principal para que no vuelvan a ser leidas

		return LoopsNL;

	}else{ //Llegando a este punto suponemos que el elemento es un nombre corto de un archivo o un directorio


		DIR_MEMORIA = 	DireccionInicio+Offset;
		mmcsd_read_data(DIR_MEMORIA, 11, name);

		DIR_MEMORIA += 	0x1A;
		mmcsd_read_data(DIR_MEMORIA, 2, Fcluster);

		DIR_MEMORIA += 	0x02;		
		mmcsd_read_data(DIR_MEMORIA, 4, SizeFile);


		if(State == 0x10)
		{
			*Tipo = 1;
			return 0x10;
		}else if(State == 0x20)
		{
			*Tipo = 0;
			return 0x20;
		}else
			return 0x00; //En caso de que fuera otro tipo de elemento se devuelve error porque la API no está preparada para tratarlo

	}

	/*Como estaba antes
	DIR_MEMORIA = ROOT_POSITION+Offset+11;

	mmcsd_read_data(DIR_MEMORIA, 1, &State);

	DIR_MEMORIA = 	ROOT_POSITION+Offset;

	mmcsd_read_data(DIR_MEMORIA, 1, &Begging);
	
	if(Begging==0x00 | Begging==0xE5)
		return Begging;

	//En caso de que sea un archivo
	if(State==0x20)
	{		
		DIR_MEMORIA = 	ROOT_POSITION+Offset;
		mmcsd_read_data(DIR_MEMORIA, 11, name);

		DIR_MEMORIA = 	ROOT_POSITION+Offset+0x1A;
		mmcsd_read_data(DIR_MEMORIA, 2, Fcluster);

		DIR_MEMORIA = 	ROOT_POSITION+Offset+0x1C;
	//	mmcsd_read_data(DIR_MEMORIA, 4, Filsize);		

		mmcsd_read_data(DIR_MEMORIA, 4, SizeFile);	
		
	}
	*/
	return 0x00;
	
}

int SacarLongitud()
{
	int ec = 0;
	int size = 0;
	int byte_checked;

	for(int j=0; j<8; j++)
	{
			ec += mmcsd_read_data(FAT_POSITION+j, 1, &byte_checked);
			if(byte_checked == 0x20)
				j=9;
			else
				size++;
	}

	return size;

}



/*
signed int format(int32 DskSize)
Summary: Formats media with a FAT filesystem.
Param: The size of the filesystem to create in kB.
Returns: EOF if there was a problem with the media, GOODEC if everything went okay.
Note: There are certain minimum and maximum size restrictions on the card and type of file system. The restrictions are as follows:
       FAT16: DskSize < 2GB
       FAT32: 33MB < DskSize < 32GB
       In order to change the way that the drive is formatted, select the proper #define(FAT16 or FAT32) way up at the top of this file.
Note: In this context, 1kB = 1024B = 2^10B. Please don't confuse this with 10^3B, we don't want to be wasting thousands of bytes of information now, do we?
Note: DskSize has a lower limit of 64, anything lower becomes highly inefficient and runs the risk of very quick corruption.
Note: If this is called on an MMC/SD card, Windows will recognize it as a RAW filesystem.
*/
/*
signed int format(int32 DskSize)
{
   int
      BPB_Media = 0xF8,
      BPB_NumFATs = 1,
      BPB_NumHeads = 2,
      BPB_SecPerClus,
      BPB_SecPerTrk = 0x20;

   int16
      BPB_BytsPerSec = 0x200,
      i;

   int32
      BPB_TotSec,
      BS_VolID = 0,
      RootDirSectors,
      TmpVal1,
      TmpVal2;

   char               
      BS_OEMName[] = "MSDOS5.0",
      BS_VolLab[] = "NO NAME    ";

#ifdef FAT32
   int
      BPB_BkBootSec = 6,
      BPB_FSInfo = 1,
      BPB_RootClus = 2,
      BS_BootSig = 0x29,
      BS_jmpBoot = 0x58,
      data[0x5A];

   int16
      BPB_RootEntCnt = 0,
      BPB_RsvdSecCnt = 32;
   
   int32 BPB_FATSz;
   
   char BS_FilSysType[] = "FAT32   ";
#else
   int
      BS_BootSig = 0x29,
      BS_jmpBoot = 0x3C,
      data[0x3E];
      
   int16
      BPB_FATSz,
      BPB_RootEntCnt = 512,
      BPB_RsvdSecCnt = 1;
      
   char BS_FilSysType[] = "FAT12   ";
#endif // #ifdef FAT32

   // initialize variables
   // figure out total sectors
   BPB_TotSec = (DskSize * 0x400) / BPB_BytsPerSec;
   
   // use the magical table on page 20 of fatgen103.pdf to determine sectors per cluster
#ifdef FAT32
   if(DskSize < 0x8400) // < 33 MB; this is too small
      return EOF;
   else if(DskSize < 0x41000) // 260 MB
      BPB_SecPerClus = 1;
   else if(DskSize < 0X800000) // 8 GB
      BPB_SecPerClus = 8;
   else if(DskSize < 0x1000000) // 16 GB
      BPB_SecPerClus = 16;
   else if(DskSize < 0x2000000) // 32 GB
      BPB_SecPerClus = 32;
   else // > 32 GB; this is too big
      return EOF;
#else
   if(DskSize < 0x1400) // < 5 MB
      BPB_SecPerClus = 1;
   else if(DskSize < 0x4000) // 16 MB
      BPB_SecPerClus = 2;
   else if(DskSize < 0X20000) // 128 MB
      BPB_SecPerClus = 4;
   else if(DskSize < 0x40000) // 256 MB
      BPB_SecPerClus = 8;
   else if(DskSize < 0x80000) // 512 MB
      BPB_SecPerClus = 16;
   else if(DskSize < 0x100000) // 1 GB
      BPB_SecPerClus = 32;
   else if(DskSize < 0x200000) // 2 GB
      BPB_SecPerClus = 64;
   else // > 2 GB; this is too big
      return EOF;
#endif // #ifdef FAT32

   // figure out how many sectors one FAT takes up
   RootDirSectors = ((BPB_RootEntCnt * 32) + (BPB_BytsPerSec - 1)) / BPB_BytsPerSec; 
   TmpVal1 = DskSize - (BPB_RsvdSecCnt + RootDirSectors); 
   TmpVal2 = (256 * BPB_SecPerClus) + BPB_NumFATs; 
#ifdef FAT32
   TmpVal2 = TmpVal2 / 2;
#endif // #ifdef FAT32 
   BPB_FATSz = (TmpVal1 + (TmpVal2 - 1)) / TmpVal2;

   // zero data
   for(i = 0; i < sizeof(data); i += 1)
      data[i] = 0;

   // start filling up data
   data[0] = 0xEB;
   data[1] = BS_jmpBoot;
   data[2] = 0x90;   
   sprintf(data + 3, "%s", BS_OEMName);
   data[11] = make8(BPB_BytsPerSec, 0);
   data[12] = make8(BPB_BytsPerSec, 1);
   data[13] = BPB_SecPerClus;
   data[14] = BPB_RsvdSecCnt;
   data[16] = BPB_NumFATs;
   data[21] = BPB_Media;
   data[24] = BPB_SecPerTrk; 
   data[26] = BPB_NumHeads;
#ifdef FAT32
   data[32] = make8(BPB_TotSec, 0);
   data[33] = make8(BPB_TotSec, 1);
   data[34] = make8(BPB_TotSec, 2);
   data[35] = make8(BPB_TotSec, 3);
   data[36] = make8(BPB_FATSz, 0);
   data[37] = make8(BPB_FATSz, 1);
   data[38] = make8(BPB_FATSz, 2);
   data[39] = make8(BPB_FATSz, 3);
   data[44] = BPB_RootClus;
   data[48] = BPB_FSInfo;
   data[50] = BPB_BkBootSec;
   data[66] = BS_BootSig;
   data[67] = make8(BS_VolID, 0);
   data[68] = make8(BS_VolID, 1);
   data[69] = make8(BS_VolID, 2);
   data[70] = make8(BS_VolID, 3);
   sprintf(data + 71, "%s", BS_VolLab);
   sprintf(data + 82, "%s", BS_FilSysType);

   // put data onto the card
   // first, all the partition parameters
   if(mmcsd_write_data(0, sizeof(data), data) != GOODEC)
      return EOF;

   // figure out where the first FAT starts
   TmpVal1 = BPB_BytsPerSec * BPB_RsvdSecCnt;

   // figure out where the root directory starts
   TmpVal2 = TmpVal1 + (BPB_NumFATs * BPB_FATSz);

   // clear out some values in data
   for(i = 0; i < 0x20; i += 1)
      data[i] = 0;

   // get rid of everything in the root directory
   clear_cluster(2);
   
   // clear out the FAT
   for(i = 0; i < BPB_FATSz; i += 0x20)
      if(mmcsd_write_data(TmpVal1 + i, 0x20, data) != GOODEC)
         return EOF;

   // insert the first 12 entries into the FAT(s)
   data[0] = 0xF8;
   data[1] = 0xFF;
   data[2] = 0xFF;
   data[3] = 0x0F;
   data[4] = 0xFF;
   data[5] = 0xFF;
   data[6] = 0xFF;
   data[7] = 0x0F;
   data[8] = 0xFF;
   data[9] = 0xFF;
   data[10] = 0xFF;
   data[11] = 0x0F;
   if(mmcsd_write_data(TmpVal1, 0x20, data) != GOODEC)
      return EOF;
      
   // reset the last cluster
   i = 2;
   if(mmcsd_write_data(0x3EC, 4, &i) != GOODEC)
      return EOF;
#else
   data[17] = make8(BPB_RootEntCnt, 0);
   data[18] = make8(BPB_RootEntCnt, 1);
   data[19] = make8(BPB_TotSec, 0);
   data[20] = make8(BPB_TotSec, 1);
   data[22] = make8(BPB_FATSz, 0);
   data[23] = make8(BPB_FATSz, 1);
   data[38] = BS_BootSig;
   data[39] = make8(BS_VolID, 0);
   data[40] = make8(BS_VolID, 1);
   data[41] = make8(BS_VolID, 2);
   data[42] = make8(BS_VolID, 3);
   sprintf(data + 43, "%s", BS_VolLab);
   sprintf(data + 54, "%s", BS_FilSysType);

   // put data onto the card
   // first, all the partition parameters
   if(mmcsd_write_data(0, sizeof(data), data) != GOODEC)
      return EOF;

   // figure out where the first FAT starts
   TmpVal1 = BPB_BytsPerSec * BPB_RsvdSecCnt;

   // figure out where the root directory starts
   TmpVal2 = TmpVal1 + (BPB_NumFATs * BPB_FATSz);

   // clear out some values in data
   for(i = 0; i < 0x20; i += 1)
      data[i] = 0;

   // get rid of everything in the root directory
   for(i = 0; i < (0x20 * BPB_RootEntCnt); i += 0x20)
      if(mmcsd_write_data(TmpVal2 + i, 0x20, data) != GOODEC)
         return EOF;
   
   // clear out the FAT
   for(i = 0; i < BPB_FATSz; i += 0x20)
      if(mmcsd_write_data(TmpVal1 + i, 0x20, data) != GOODEC)
         return EOF;

   // insert the first 3 entries into the FAT(s)
   data[0] = 0xF8;
   data[1] = 0xFF;
   data[2] = 0xFF;
   if(mmcsd_write_data(TmpVal1, 0x20, data) != GOODEC)
      return EOF;
      
#endif // #ifdef FAT32

   i = 0xAA55;

   if(mmcsd_write_data(0x1FE, 2, &i) != GOODEC)
      return EOF;   

   // we're going to have to re-initialize the FAT, a bunch of parameters probably just changed
   fat_init();

   return GOODEC;
}
*/

#endif // #ifndef FAT_PIC_C

