
package com.cliente_bluetooth;
 
import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
 
public class CustomListAdapter extends ArrayAdapter<String>{
 
private final Activity context;
private final String[] itemname;
private final Integer[] TamañoElemento;
private final Integer[] imgid;

	public CustomListAdapter(Activity context, String[] itemname, Integer[] TamañoElemento, Integer[] imgid) {
		
		super(context, R.layout.mylist, itemname);
		// TODO Auto-generated constructor stub
		this.context=context;
		this.itemname=itemname;
		this.TamañoElemento=TamañoElemento;
		this.imgid=imgid;
		
	}

	public void onItemClick(AdapterView<?> arg0, View v, int position, long arg3) {
	    // TODO Auto-generated method stub
		
		System.out.println("La posicion es: "+position);
	   /* Intent i;
	    String name = adapter1.getItem(position);
	            Log.d("id", name);
	    if (name.equals("Item1"))
	    {
	        i = new Intent(this, anActivity.class);
	        startActivity(i);
	    }
	    else if (name.equals("Item2"))
	    {
	        i = new Intent(this, anActivity2.class);
	        startActivity(i);
	    }
	}*/
	}
	
	
	public View getView(int position,View view,ViewGroup parent) {
	
		LayoutInflater inflater=context.getLayoutInflater();
		
		View rowView=inflater.inflate(R.layout.mylist, null,true);
		
		TextView txtTitle = (TextView) rowView.findViewById(R.id.item);
		
		ImageView imageView = (ImageView) rowView.findViewById(R.id.icon);
		
		TextView extratxt = (TextView) rowView.findViewById(R.id.textView1);
		
		txtTitle.setText(itemname[position]);
		
		imageView.setImageResource(imgid[position]);
		
		if(imgid[position]!=R.drawable.icon_folder)	
		{

			float TamañoFloat =(float) (TamañoElemento[position]/1000.0);
			String StringAuxTamañoFloat = String.format("%.2f", TamañoFloat);
			
			extratxt.setText("Tamaño del archivo "+StringAuxTamañoFloat+" Kbytes");
		}else{
			
			extratxt.setText("");
		}
		
		return rowView;
	
	};
}
