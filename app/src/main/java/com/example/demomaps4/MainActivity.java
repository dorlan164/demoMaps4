package com.example.demomaps4;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;
import com.google.android.gms.tasks.OnSuccessListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;


public class MainActivity extends AppCompatActivity implements OnMapReadyCallback,
        GoogleMap.OnMarkerClickListener {

    private static final int LOCATION_PERMISSION_REQUEST = 1000;

    private GoogleMap map;
    private ListView lvClientes;
    private ClienteAdapter adapter;
    private int selectedPos = 0;

    // Para rutas
    private FusedLocationProviderClient fusedLocationClient;
    private LatLng currentLocation;  // se llena en getLastLocation()

    // Datos de ejemplo
    private final LatLng[][] coords = {
            { new LatLng(28.6330, -106.0691), new LatLng(28.6392, -106.0824) },
            { new LatLng(21.161907, -86.851528),  new LatLng(25.686614, -100.316113) },
            { new LatLng(19.041297, -98.206200),  new LatLng(20.967370, -89.592586) }
    };
    private final String[] direccionesTexto = {
            "Avenida Universidad 4700, Col. Universidad, Chihuahua, CHH",
            "Calle 8 123, Playa del Carmen, QR",
            "Calle 14 56, Mérida, Yucatán"
    };
    private final int[] carouselImages = {
            R.drawable.img1,
            R.drawable.img2,
            R.drawable.img3
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // --- Inicializar cliente de localización ---
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);
        } else {
            getLastLocation();
        }

        // --- Lista de clientes ---
        lvClientes = findViewById(R.id.listViewClientes);
        lvClientes.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        lvClientes.setSelector(android.R.color.transparent);

        List<String> listaItems = new ArrayList<>();
        String bullet = "\u2022 ";
        for (int i = 0; i < coords.length; i++) {
            String item = "Cliente " + (i+1) + ":\n"
                    + bullet + coords[i][0].latitude + ", " + coords[i][0].longitude + "\n"
                    + bullet + coords[i][1].latitude + ", " + coords[i][1].longitude + "\n"
                    + bullet + direccionesTexto[i];
            listaItems.add(item);
        }

        adapter = new ClienteAdapter(listaItems);
        lvClientes.setAdapter(adapter);
        lvClientes.setItemChecked(selectedPos, true);

        lvClientes.setOnItemClickListener((parent, view, pos, id) -> {
            selectedPos = pos;
            lvClientes.setItemChecked(pos, true);
            adapter.notifyDataSetChanged();
            if (map != null) mostrarPines(pos);
        });

        // --- Map fragment ---
        SupportMapFragment mapFrag = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.map);
        mapFrag.getMapAsync(this);
    }

    // Resultado petición permiso
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getLastLocation();
        }
    }

    // Obtiene la última ubicación conocida
    private void getLastLocation() {
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        currentLocation = new LatLng(
                                location.getLatitude(),
                                location.getLongitude()
                        );
                    }
                });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        map.setOnMarkerClickListener(this);
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(
                new LatLng(20.0,-100.0), 4f));
        mostrarPines(selectedPos);
    }

    // Limpia marcadores y añade los del cliente idx
    private void mostrarPines(int idx) {
        map.clear();
        LatLng p1 = coords[idx][0], p2 = coords[idx][1];
        map.addMarker(new MarkerOptions().position(p1)
                .title("Dirección 1")
                .icon(BitmapDescriptorFactory.defaultMarker(
                        BitmapDescriptorFactory.HUE_RED)));
        map.addMarker(new MarkerOptions().position(p2)
                .title("Dirección 2")
                .icon(BitmapDescriptorFactory.defaultMarker(
                        BitmapDescriptorFactory.HUE_GREEN)));
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(p1,8f));

        // Tercera dirección vía geocoder
        new Thread(() -> {
            try {
                List<Address> res = new Geocoder(this, Locale.getDefault())
                        .getFromLocationName(direccionesTexto[idx],1);
                if (!res.isEmpty()) {
                    LatLng p3 = new LatLng(
                            res.get(0).getLatitude(),
                            res.get(0).getLongitude());
                    runOnUiThread(() -> {
                        map.addMarker(new MarkerOptions().position(p3)
                                .title("Dirección 3")
                                .icon(BitmapDescriptorFactory.defaultMarker(
                                        BitmapDescriptorFactory.HUE_BLUE)));
                        LatLngBounds b = new LatLngBounds.Builder()
                                .include(p1).include(p2).include(p3).build();
                        map.animateCamera(
                                CameraUpdateFactory.newLatLngBounds(b,100));
                    });
                }
            } catch(IOException e){e.printStackTrace();}
        }).start();
    }

    // Al clickear en un marker
    @Override
    public boolean onMarkerClick(Marker marker) {
        LatLng destino = marker.getPosition();
        CharSequence[] opts = {
                "Ruta en App",
        //        "Ruta en Maps",
                "Street View"
        };
        new AlertDialog.Builder(this)
                .setTitle(marker.getTitle())
                .setItems(opts, (d,w) -> {
                    if (w==0) {
                        // dibujar en app
                        if (currentLocation!=null) {
                            drawRoute(currentLocation, destino);
                        }
                  //  } //else if (w==1) {
                        // navegación externa
                      //  Intent i = new Intent(Intent.ACTION_VIEW,
                    //            Uri.parse("google.navigation:q="
                       //                 +destino.latitude+","+destino.longitude));
                      //  i.setPackage("com.google.android.apps.maps");
                     //   startActivity(i);
                    } else {
                        // Street View
                        Intent i = new Intent(Intent.ACTION_VIEW,
                                Uri.parse("google.streetview:cbll="
                                        +destino.latitude+","+destino.longitude));
                        i.setPackage("com.google.android.apps.maps");
                        startActivity(i);
                    }
                }).show();
        return true;
    }

    // Construye URL para Directions API
    private String getDirectionsUrl(LatLng ori, LatLng dst) {
        String o = "origin="+ori.latitude+","+ori.longitude;
        String d = "destination="+dst.latitude+","+dst.longitude;
        String key = "key="+getString(R.string.google_maps_key);
        return "https://maps.googleapis.com/maps/api/directions/json?"
                + o +"&"+ d +"&"+ key;
    }

    // Lanza la petición y parsea la respuesta
    private void drawRoute(LatLng ori, LatLng dst) {
        String url = getDirectionsUrl(ori,dst);
        new AsyncTask<String,Void,String>(){
            @Override protected String doInBackground(String... urls) {
                try {
                    OkHttpClient c = new OkHttpClient();
                    Request r = new Request.Builder()
                            .url(urls[0]).build();
                    Response resp = c.newCall(r).execute();
                    return resp.body().string();
                } catch(Exception e){e.printStackTrace();}
                return null;
            }
            @Override protected void onPostExecute(String json) {
                if (json!=null) new AsyncTask<String,Void,List<LatLng>>(){
                    @Override protected List<LatLng> doInBackground(String... jd) {
                        List<LatLng> pts = new ArrayList<>();
                        try {
                            JSONObject jObj = new JSONObject(jd[0]);
                            JSONArray routes = jObj.getJSONArray("routes");
                            if (routes.length()>0) {
                                JSONArray steps = routes
                                        .getJSONObject(0)
                                        .getJSONArray("legs")
                                        .getJSONObject(0)
                                        .getJSONArray("steps");
                                for (int i=0;i<steps.length();i++){
                                    String poly = steps
                                            .getJSONObject(i)
                                            .getJSONObject("polyline")
                                            .getString("points");
                                    pts.addAll(decodePoly(poly));
                                }
                            }
                        } catch(Exception e){e.printStackTrace();}
                        return pts;
                    }
                    @Override protected void onPostExecute(List<LatLng> pts) {
                        if (map!=null && !pts.isEmpty()) {
                            map.addPolyline(new PolylineOptions()
                                    .addAll(pts).width(10));
                            // centrar cámara entre origen y destino
                            LatLngBounds b = new LatLngBounds.Builder()
                                    .include(pts.get(0))
                                    .include(pts.get(pts.size()-1))
                                    .build();
                            map.animateCamera(
                                    CameraUpdateFactory.newLatLngBounds(b,100));
                        }
                    }
                }.execute(json);
            }
        }.execute(url);
    }

    // Decodifica la polyline de Google
    private List<LatLng> decodePoly(String encoded) {
        List<LatLng> poly = new ArrayList<>();
        int index=0, len=encoded.length(), lat=0, lng=0;
        while (index < len) {
            int b, shift=0, result=0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b>=0x20);
            int dlat = ((result&1)!=0 ? ~(result>>1) : (result>>1));
            lat += dlat;
            shift=0; result=0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b>=0x20);
            int dlng = ((result&1)!=0 ? ~(result>>1) : (result>>1));
            lng += dlng;
            poly.add(new LatLng(
                    lat/1e5, lng/1e5
            ));
        }
        return poly;
    }

    // --- Adaptador de la lista (igual que antes, con el botón de fragment) ---
    private class ClienteAdapter extends ArrayAdapter<String> {
        ClienteAdapter(List<String> items) {
            super(MainActivity.this,
                    R.layout.list_item_cliente,
                    R.id.textoCliente, items);
        }
        @NonNull @Override
        public View getView(int pos, View cv, ViewGroup parent) {
            View v = super.getView(pos,cv,parent);
            v.findViewById(R.id.root)
                    .setActivated(pos==selectedPos);
            ImageView btn = v.findViewById(R.id.btn_images);
            btn.setOnClickListener(x -> {
                FullscreenCarouselFragment f =
                        FullscreenCarouselFragment
                                .newInstance(carouselImages, pos);
                FragmentManager fm = getSupportFragmentManager();
                fm.beginTransaction()
                        .replace(R.id.fragmentContainer, f)
                        .addToBackStack(null)
                        .commit();
            });
            return v;
        }
    }
}
