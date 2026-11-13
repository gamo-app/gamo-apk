//
//  AppConfig.swift
//  GamoEkkIOS
//
//  Constantes portadas 1:1 desde MainActivity.java (Android).
//  Se conservan URLs, claves de preferencias y nombres de páginas locales.
//

import Foundation

enum AppConfig {

    // MARK: - Sitio y actualizaciones (idénticos a Android)

    static let siteURL = "https://gamo-ekk.pages.dev/"

    static let updateVersionURL =
        "https://github.com/gamo-app/gamo-apk/releases/latest/download/version.json"

    static let updateFallbackURL =
        "https://github.com/gamo-app/gamo-apk/releases/latest/download/gamo.apk"

    // MARK: - Preferencias (equivalente a SharedPreferences "GamoPrefs")

    static let keyIdUsuario = "id_usuario"
    static let keyUsuarioSesion = "usuario_sesion"
    static let keyVersionIgnorada = "version_ignorada"
    static let keyNotifPedido = "notif_pedido"
    static let keyUuidUsuario = "uuid_usuario"
    static let keyNombre = "nombre"
    static let keyApellido = "apellido"
    static let keyEmail = "email"
    static let keyRol = "rol"

    // MARK: - Assets locales (carpeta de referencia "WebAssets" en el bundle)

    static let webAssetsFolder = "WebAssets"
    static let offlinePage = "sinconexion"
    static let qrOfflinePage = "qrsinconexion"
    static let updatePage = "actualizacion"

    // MARK: - Color inicial de navegación (Android: Color.WHITE)

    static let initialNavigationColor = "#FFFFFF"

    // MARK: - JS de fondo dinámico (copia literal del original)

    static let dynamicBackgroundJS = #"""
    (function(){
    if(window.__themeObserverStarted)return;
    window.__themeObserverStarted=true;
    function update(){
    var c=getComputedStyle(document.documentElement).backgroundColor;
    if(!c||c==='transparent'||c==='rgba(0, 0, 0, 0)'){
    c=getComputedStyle(document.body).backgroundColor;
    }
    if(!c||c==='transparent'||c==='rgba(0, 0, 0, 0)')return;
    var m=c.match(/[\d.]+/g);
    if(m&&m.length>=3&&c.indexOf('rgb')===0){
    if(m.length>=4&&Number(m[3])===0)return;
    c='#'+m.slice(0,3).map(function(n){
    var h=Math.max(0,Math.min(255,Math.round(Number(n)))).toString(16);
    return h.length<2?'0'+h:h;
    }).join('');
    }
    if(window.AndroidApp){
    window.AndroidApp.setNavigationBackground(c);
    }
    }
    update();
    new MutationObserver(update).observe(document.documentElement,
    {attributes:true,childList:true,subtree:true});
    if(document.body){
    new MutationObserver(update).observe(document.body,
    {attributes:true,childList:true,subtree:true});
    }
    window.addEventListener('load',update);
    window.addEventListener('pageshow',update);
    document.addEventListener('visibilitychange',update);
    window.setInterval(update,1000);
    })();
    """#

    // MARK: - JS de notificaciones (copia literal del original)

    static let notificationShimJS = #"""
    (function(){
    if(window.__gamoNotifShim)return;
    if(!window.AndroidApp
    ||typeof window.AndroidApp.mostrarNotificacion!=='function')return;
    window.__gamoNotifShim=true;
    var pendientes={};
    function crear(titulo,opciones){
    opciones=opciones||{};
    this.title=titulo==null?'':String(titulo);
    this.body=opciones.body==null?'':String(opciones.body);
    this.tag=opciones.tag;
    this.onclick=null;this.onclose=null;this.onerror=null;
    var self=this;
    setTimeout(function(){try{
    AndroidApp.mostrarNotificacion(self.title,self.body);
    }catch(e){}},0);
    }
    crear.requestPermission=function(cb){
    return new Promise(function(resolve){
    var id='n'+Date.now()+Math.floor(Math.random()*100000);
    pendientes[id]=function(r){resolve(r);
    if(typeof cb==='function')cb(r);};
    try{AndroidApp.pedirPermisoNotificaciones(id);}
    catch(e){delete pendientes[id];resolve('denied');}
    });
    };
    window.__gamoNotifResultado=function(id,r){
    var f=pendientes[id];if(!f)return;delete pendientes[id];f(r);
    };
    try{Object.defineProperty(crear,'permission',{
    configurable:true,get:function(){
    try{return AndroidApp.estadoNotificaciones();}
    catch(e){return 'default';}
    }});}catch(e){}
    try{Object.defineProperty(window,'Notification',{
    configurable:true,writable:true,value:crear});
    }catch(e){try{window.Notification=crear;}catch(e2){}}
    })();
    """#

    // MARK: - Puente JS de AndroidApp

    /// En Android, `addJavascriptInterface(obj, "AndroidApp")` expone métodos
    /// sincrónicos. En WKWebView la comunicación es asincrónica vía
    /// `postMessage`, por lo que se inyecta este shim que conserva la misma
    /// API `window.AndroidApp.*` usada por el sitio y por las páginas locales.
    static func androidAppShim() -> String {
        return #"""
        (function(){
        if(window.__gamoShimInstalled)return;
        window.__gamoShimInstalled=true;
        function post(action,payload){
        try{
        var msg=payload||{};
        msg.action=action;
        window.webkit.messageHandlers.AndroidApp.postMessage(msg);
        }catch(e){}
        }
        window.AndroidApp={
        setNavigationBackground:function(c){
        post('setNavigationBackground',{color:String(c)});
        },
        guardarIdUsuario:function(id){
        post('guardarIdUsuario',{id:String(id)});
        },
        guardarUsuarioSesion:function(d){
        post('guardarUsuarioSesion',{datos:String(d)});
        },
        limpiarUsuarioSesion:function(){
        post('limpiarUsuarioSesion',{});
        },
        abrirQrSinConexion:function(){
        post('abrirQrSinConexion',{});
        },
        mostrarNotificacion:function(titulo,cuerpo){
        post('mostrarNotificacion',{
        titulo:titulo==null?'':String(titulo),
        cuerpo:cuerpo==null?'':String(cuerpo)
        });
        },
        estadoNotificaciones:function(){
        return window.__gamoNotifState||'default';
        },
        pedirPermisoNotificaciones:function(id){
        post('pedirPermisoNotificaciones',{callbackId:String(id)});
        },
        tienePermisoCamara:function(){
        return !!window.__gamoCamPerm;
        },
        comprobarActualizacion:function(){
        post('comprobarActualizacion',{});
        },
        retry:function(){
        post('retry',{});
        },
        obtenerUsuarioSesion:function(){
        return window.__gamoUsuarioSesion||'{}';
        },
        volverSinConexion:function(){
        post('volverSinConexion',{});
        },
        descargarActualizacion:function(){
        post('descargarActualizacion',{});
        },
        omitirActualizacion:function(){
        post('omitirActualizacion',{});
        }
        };
        window.__gamoNotifResultado=window.__gamoNotifResultado||function(){};
        })();
        """#
    }

    /// Estado inicial que el shim expone de forma sincrónica.
    static func bootstrapScript(sessionJSON: String,
                                cameraGranted: Bool,
                                notifState: String) -> String {
        let sessionLiteral = jsStringLiteral(sessionJSON)
        let notifLiteral = jsStringLiteral(notifState)
        let cameraLiteral = cameraGranted ? "true" : "false"

        return """
        window.__gamoUsuarioSesion = \(sessionLiteral);
        window.__gamoCamPerm = \(cameraLiteral);
        window.__gamoNotifState = \(notifLiteral);
        """
    }

    /// Convierte un String en un literal JS/JSON seguro.
    static func jsStringLiteral(_ value: String) -> String {
        guard let data = try? JSONSerialization.data(withJSONObject: [value],
                                                    options: []),
              let arrayString = String(data: data, encoding: .utf8) else {
            return "\"\""
        }
        // arrayString es ["..."]; se quitan los corchetes externos.
        return String(arrayString.dropFirst().dropLast())
    }

    /// Convierte un diccionario en JSON para evaluar en JS.
    static func jsonObjectString(_ object: [String: Any]) -> String {
        guard let data = try? JSONSerialization.data(withJSONObject: object,
                                                    options: []),
              let string = String(data: data, encoding: .utf8) else {
            return "{}"
        }
        return string
    }
}
