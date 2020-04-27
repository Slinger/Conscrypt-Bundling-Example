package com.example.myapplication;

import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.conscrypt.Conscrypt;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.Arrays;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


public class MainActivity extends AppCompatActivity {

    //good test pages (be prepared to read html directly)
    public String url = "https://clienttest.ssllabs.com:8443/ssltest/viewMyClient.html"; //will show if TLSv1.3 works and supported protocols
    //public String url= "https://tls-v1-0.badssl.com:1010/"; //TLSv1.0 (should fail)
    //public String url= "https://tls-v1-2.badssl.com:1012/"; //TLSv1.2 (should work)

    TextView txtString;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtString = (TextView) findViewById(R.id.this_is_id_name);
        txtString.setMovementMethod(new ScrollingMovementMethod());


        //insert bundled conscrypt at the top of list of providers, will be used instead of OS version
        Security.insertProviderAt(Conscrypt.newProvider(), 1);


        OkHttpClient.Builder okHttpBuilder = new OkHttpClient()
                .newBuilder();

        //use custom socket factory to enable wanted protocols
        try {
            okHttpBuilder.sslSocketFactory(new InternalSSLSocketFactory(), trustManager());
        } catch (Exception e) {
            e.printStackTrace();
        }

        OkHttpClient client = okHttpBuilder.build();

        //time to demonstrate...
        txtString.setText("Requesting " + url);


        Request request = new Request.Builder()
                .url(url)
                .build();

            client.newCall(request).enqueue(new Callback() {

                @Override
                public void onFailure(Call call, IOException e) {
                    MainActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            txtString.setText("Failed to request page (this could be a good thing, if the page only supported something old like SSLv3, TLSv1.0 or TLSv1.1 which are disabled)");
                        }
                    });

                    e.printStackTrace();
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    final String myresponse = response.body().string();

                    MainActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            txtString.setText(myresponse);
                        }
                    });
                }
            });
    }

    //just replicate the default
    private static X509TrustManager trustManager() {
        try {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init((KeyStore) null);
            TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
            if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager)) {
                throw new IllegalStateException("Unexpected default trust managers:"
                        + Arrays.toString(trustManagers));
            }
            return (X509TrustManager) trustManagers[0];
        } catch (Exception e) {
            //Log.e(TAG, Log.getStackTraceString(e));
            return null;
        }
    }

    //enable TLSv1.3 and TLSv1.2 (and no older, deprecated, protocols)
    private static class InternalSSLSocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory factory;

        private InternalSSLSocketFactory() throws NoSuchAlgorithmException, KeyManagementException {
            SSLContext context = SSLContext.getInstance("TLSv1.3");
            context.init(null, null, null);
            factory = context.getSocketFactory();
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return factory.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return factory.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket() throws IOException {
            return configure(factory.createSocket());
        }

        @Override
        public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            return configure(factory.createSocket(s, host, port, autoClose));
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return configure(factory.createSocket(host, port));
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            return configure(factory.createSocket(host, port, localHost, localPort));
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return configure(factory.createSocket(host, port));
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
            return configure(factory.createSocket(address, port, localAddress, localPort));
        }

        private Socket configure(Socket socket) {
            if (socket instanceof SSLSocket)
                ((SSLSocket) socket).setEnabledProtocols(new String[] {"TLSv1.3" , "TLSv1.2"});
            return socket;
        }
    }

}

