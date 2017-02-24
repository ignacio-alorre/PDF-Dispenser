///////////////////////////////////////////////////////////////////////////
////                            LCD_ATE4B.C                            ////
////                            (versión 01)                           ////
////                Driver para LCDs basados en HD44780                ////
////                                                                   ////
////   Basado en el driver 'LCD.C' incluido con el compilador C de CCS ////
////  versión 3.234.                                                   ////
////                                                                   ////
////   Válido para ser utilizado con la versión antigual de las tarje- ////
//// tas PICDEM2 PLUS de Microchip (las rojas).                        ////
////                                                                   ////
////   Modificaciones efectuadas por Miguel Ángel José Prieto          ////
////                                                                   ////
//// ----------------------------------------------------------------- ////
////                                                                   ////
////  FUNCIONES DEFINIDAS                                              ////
////  ===================                                              ////
////                                                                   ////
////  lcd_init()      Debe llamarse antes que cualquier otra función   ////
////                                                                   ////
////  lcd_putc(c)     Muestra 'c' en la siguiente posición del LCD     ////
////                        Caracteres con significado especial        ////
////                         \f  Borra display                         ////
////                         \n  Va al inicio de la segunda línea      ////
////                         \b  Retrocede una posición                ////
////                                                                   ////
////  lcd_gotoxy(x,y) Fija la posición del LCD donde escribir          ////
////                                                                   ////
////  lcd_getc(x,y)   Devuelve el carácter en la posición x,y del LCD  ////
////                                                                   ////
///////////////////////////////////////////////////////////////////////////
////                                                                   ////
//// v. 01  * Se pasan las líneas de datos del LCD a los cuatro bits   ////
////        más bajos del puerto.                                      ////
////        * Se ubican las líneas de control en tres bits de PORTA.   ////
////        * Se traducen los comentarios al español.                  ////
////                                                                   ////
///////////////////////////////////////////////////////////////////////////

// Los pines a conectar se definen en una estructura como sigue:
//     RA1  enable
//     RA2  rw
//     RA3  rs
//     RX0  D4
//     RX1  D5
//     RX2  D6
//     RX3  D7
//
// X puede ser el puerto D o el puerto B en este fichero.
// Sólo se usan los cuatro bits más bajos del puerto X y tres bits de PORTA.
// No se usan los pines D0-D3 del LCD.




struct lcd_pin_map
       {                          // Esta estructura se mapea en un
           int      otros : 4;     // puerto de E/S para acceder a
           int      data: 4;     // los pines de datos del LCD.
       } lcd;


// en el puerto B (dirección F81h)
#byte lcd = 0xF81              

#define set_tris_lcd(x) set_tris_b(x)


struct lcd_ctrl_map
       {                          // Esta estructura se mapea en el
           				       // PORTA para acceder a los pines
           BOOLEAN  rs;       // de control del LCD.
           BOOLEAN  rw;           // El pin 'enable' se mapea en RA1,
           BOOLEAN  enable;           // el pin 'rw', en RA2, y el pin
		   BOOLEAN  unused;

           int      otros: 4;     // 'rs', RA3.
       } lcd_ctrl;


 // Aquí se mapea la estructura en el puerto A (dirección 0xF80
 #byte lcd_ctrl = 0xF80         



#define set_tris_ctrl(x) set_tris_a(x)



#define lcd_type 2           // 0=5x7, 1=5x10, 2=2 líneas
#define lcd_line_two 0x40    // Dirección RAM del LCD para la segunda línea


// Estos bytes se mandan al LCD para inicializarlo.
BYTE const LCD_INIT_STRING[4] = {0x20 | (lcd_type << 2), 0xc, 1, 6};


// Estas constantes se usan para configurar los puertos de E/S.
// En modo escritura, los pines de datos son salidas.
// En modo lectura, los pines de datos son entradas.
struct lcd_pin_map const LCD_WRITE = {0,0};
struct lcd_pin_map const LCD_READ = {0,15};




///////////////////////////////////////////////////////////////////////////
BYTE lcd_read_byte()
{
      BYTE low,high;

      set_tris_lcd(LCD_READ);
      lcd_ctrl.rw = 1;
      delay_cycles(2);
      lcd_ctrl.enable = 1;
      delay_cycles(4);
      high = lcd.data;
      lcd_ctrl.enable = 0;
      delay_cycles(8);
      lcd_ctrl.enable = 1;
      delay_us(1);
      low = lcd.data;
      lcd_ctrl.enable = 0;
      set_tris_lcd(LCD_WRITE);
      return( (high<<4) | low);
}




///////////////////////////////////////////////////////////////////////////
void lcd_send_nibble(BYTE n)
{
      lcd.data = n;
      delay_cycles(3);
      lcd_ctrl.enable = 1;
      delay_us(4);
      lcd_ctrl.enable = 0;
}




///////////////////////////////////////////////////////////////////////////
void lcd_send_byte(BYTE address,BYTE n)
{
      lcd_ctrl.rs = 0;
      while (bit_test(lcd_read_byte(),7));
      lcd_ctrl.rs = address;
      delay_cycles(3);
      lcd_ctrl.rw = 0;
      delay_cycles(3);
      lcd_ctrl.enable = 0;
      lcd_send_nibble(n >> 4);
      lcd_send_nibble(n & 0xf);
}




///////////////////////////////////////////////////////////////////////////
void lcd_init()
{
    BYTE i;

    //setup_adc_ports(NO_ANALOGS);  // Configura PORTA como E/S digitales.
    //set_tris_ctrl(0b000000);      // Configura RA<1:3> como salidas.

    set_tris_lcd(LCD_WRITE);
    lcd_ctrl.rs = 0;
    lcd_ctrl.rw = 0;
    lcd_ctrl.enable = 0;
    delay_ms(15);
    for(i=1;i<=3;++i)
    {
       lcd_send_nibble(3);
       delay_ms(5);
    }
    lcd_send_nibble(2);
    for(i=0;i<=3;++i)
       lcd_send_byte(0,LCD_INIT_STRING[i]);
}




///////////////////////////////////////////////////////////////////////////
void lcd_gotoxy(BYTE x,BYTE y)
{
   BYTE address;

   if(y!=1)
     address=lcd_line_two;
   else
     address=0;
   address+=x-1;
   lcd_send_byte(0,0x80|address);
}




///////////////////////////////////////////////////////////////////////////
void lcd_putc(char c)
{
   switch (c)
   {
     case '\f':  lcd_send_byte(0,1);
                 delay_ms(5);
                 break;

     case '\n':  lcd_gotoxy(1,2);
                 break;

     case '\b':  lcd_send_byte(0,0x10);
                 break;

     default  :  lcd_send_byte(1,c);
                 break;
   }
}




///////////////////////////////////////////////////////////////////////////
char lcd_getc(BYTE x, BYTE y)
{
   char value;

    lcd_gotoxy(x,y);
    while (bit_test(lcd_read_byte(),7)); // Espera por LCD desocupado.
    lcd_ctrl.rs=1;
    value = lcd_read_byte();
    lcd_ctrl.rs=0;
    return(value);
}
