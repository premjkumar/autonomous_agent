package com.example.roboqwen  

import android.content.Context  
import android.net.nsd.NsdManager  
import android.net.nsd.NsdServiceInfo  
import android.util.Log  

class NsdHelper(context: Context, private val onResolved: (String) -> Unit) {  
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager  
    private val serviceType = "_ollama._tcp."  
    private var discoveryListener: NsdManager.DiscoveryListener? = null  

    fun startDiscovery() {  
        stopDiscovery()  
          
        discoveryListener = object : NsdManager.DiscoveryListener {  
            override fun onStartDiscoveryFailed(st: String?, err: Int) {   
                Log.e("NSD_DRIVER", "Discovery initialization failure code: $err")  
                nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, this)   
            }  
            override fun onStopDiscoveryFailed(st: String?, err: Int) {}  
            override fun onDiscoveryStarted(regType: String?) { Log.d("NSD_DRIVER", "Scan pipeline initialized.") }  
            override fun onDiscoveryStopped(regType: String?) {}  
              
            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {  
                if (serviceInfo?.serviceType == serviceType) {  
                    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {  
                        override fun onResolveFailed(si: NsdServiceInfo?, err: Int) {  
                            Log.e("NSD_DRIVER", "Failed to resolve metadata: $err")  
                        }  
                        override fun onServiceResolved(resolvedInfo: NsdServiceInfo?) {  
                            resolvedInfo?.let {  
                                val actualHostIp = it.host.hostAddress?.replace("/", "") ?: ""  
                                val actualPort = it.port  
                                  
                                if (actualHostIp.isNotBlank()) {  
                                    val compiledEndpoint = "http://$actualHostIp:$actualPort/api/chat"  
                                    Log.d("NSD_DRIVER", "Discovered endpoint: $compiledEndpoint")  
                                    onResolved(compiledEndpoint)  
                                }  
                            }  
                        }  
                    })  
                }  
            }  
            override fun onServiceLost(si: NsdServiceInfo?) { Log.w("NSD_DRIVER", "Service link dropped.") }  
        }  
        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)  
    }  

    fun stopDiscovery() {  
        try { discoveryListener?.let { nsdManager.stopServiceDiscovery(it) } } catch (e: Exception) {}  
        discoveryListener = null  
    }  
}
