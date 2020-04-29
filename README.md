# An example and guide on how to bundle a modern Security Provider (Conscrypt) directly with an app. Without using Google Services.
My motivation for this is to explain how I did my fork of [AntennaPod](https://github.com/AntennaPod/AntennaPod) with Conscrypt bundled to make it work on older devices with no support for modern protocols. See [AntennaPod CryptoBundle](https://github.com/Slinger/AntennaPod_CryptoBundle/).

In short this is how you can bundle Conscrypt directly in your app. Since most Android devices these days have not been updated in a long time, using a modern Security Provider means you app gets all the latest protocols and cipher suites (and security fixes) even when running on an old Android version. The total app size will increase, how much depends on what libraries are already included, but it can be a few megabytes.

Nothing here is my invention. I've just borrowed different parts from different places and glued it together. This example was my first experiment/proof-of-concept before I tried modifying AntennaPod. It's just based on one of the standard starting projects Android Studio creates. And apart from building it to try it yourself, only two files should be of interest: the [gradle file](app/build.gradle) and [java file](app/src/main/java/com/example/myapplication/MainActivity.java) containing the magic.

Ironically, after doing this I've started to see that bundling Conscrypt has already been mentioned at different places (like a NetCipher example and the OkHttp documentation). But I hope this page will help make it more visible and help people see how a complete implementation could look like.

My dream would be that someone makes an app with a minimal API that offers something similar to Google's "ProviderInstaller" (maybe a "ConscrypInstaller"?), which could then be installed and updated through F-Droid. And through it all apps could access a modern Conscrypt (instead of each one having to bundle a copy or rely on Google's services). Due to limited time and experience with Java/Android I don't think I'm the right person to do this.

## Bundling Conscrypt, the basics.
Look at the two files liked to above for exact details. Most repositories offers Conscrypt, so just add to the gradle dependensies:
```
implementation 'org.conscrypt:conscrypt-android:VERSION'
```
where "VERSION" should be kept up to date (at the time of writing 2.4.0), or set to "latest.release". The later might seem like the obvious choice, but has some problems: it makes it harder to verify reproducible builds and the library might get updated in some way that affects functionality without anyone noticing and testing before release.

And then at some suitable place in your code (before doing any networking, in the same place you might put Google's ProviderInstaller), just call:

```
Security.insertProviderAt(Conscrypt.newProvider(), 1);
```
to insert the bundled Conscrypt to the top of security providers. You will also need to add some imports (like org.conscrypt.Conscrypt and java.security.Security). Lets just say I just relied on Android Studio to suggest what to import for my code.

And... That's it! Congratulations, your app will now use the bundled Conscrypt instead of the old OS version! From my testing this enables TLSv1.2 and all modern cipher suites on all devices, and TLSv1.3 on most.


## Enabling TLSv1.3, hardening security.
Since we know Conscrypt supports TLSv1.3 we can go ahead and enable TLSv1.3. On most Android versions it will get enabled by default, but some (like KitKat) it will not. Also some obsolete protocols still gets enabled by default on all android versions: SSLv3, TLSv1.0 and TLSv1.1. So since we don't need them, it makes sense to disable them while we're at it.

How this is done varies, but with OkHttp (which is used by AntennaPod) there is a well know approach of using a custom ssl socket factory. This has been floating around for quite some time as a way of enabling TLSv1.2 on KitKat, but we're going to use it for all android versions to also enable TLSv1.3 and make sure no legacy protocols are enabled.

To override the default socket factory it's enough to do:
```
OkHttpClient.Builder okHttpBuilder = new OkHttpClient().newBuilder();
...
okHttpBuilder.sslSocketFactory(new socketFactory(), trustManager());
...
OkHttpClient client = okHttpBuilder.build();
```

And then use "client" as usual. Se the example code for details (and exception checks) Here trustManager just replicates the default trust manager (borrowed from AntennaPod). The socketFactory class is the important thing:

```
    //enable TLSv1.3 and TLSv1.2 (and no older, deprecated, protocols)
    private static class socketFactory extends SSLSocketFactory {
        private final SSLSocketFactory factory;

        private socketFactory() throws NoSuchAlgorithmException, KeyManagementException {
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

```

Note that the SSLContext used for the SSLSocketFactory is requested with "TLSv1.3", and that setEnabledProtocols only gets "TLSv1.3" and  "TLSv1.2". This should be enough to ensure TLSv1.2 and TLSv1.3 are enabled even on old devices. And that none of the legacy protocols are enabled.


## The Example

The example app can be built using gradle or through android studio as usual. When run it will fetch a webpage and display it (in plain html). In the [java file](app/src/main/java/com/example/myapplication/MainActivity.java) around line 36 are 3 urls that I found useful for experimenting:
```
    public String url = "https://clienttest.ssllabs.com:8443/ssltest/viewMyClient.html"; //will show if TLSv1.3 works and supported protocols
    //public String url= "https://tls-v1-0.badssl.com:1010/"; //TLSv1.0 (should fail)
    //public String url= "https://tls-v1-2.badssl.com:1012/"; //TLSv1.2 (should work)

```
The first one is the most interesting: if you scroll through the html code, about halfway you will see a list of supported protocols like TLSv1.3 (only the highest version is just ignore the rest), and further on a list of cipher suites. The other two are just to check older protocols (again, the ssllabs page does not tell the truth without a browser running javascript). The TLSv1.2 should work and TLSv1.0 fail.

