/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package javax.xml.ws.spi;

import java.io.InputStream;
import java.io.File;
import java.io.FileInputStream;

import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Properties;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import javax.xml.ws.WebServiceException;

class FactoryFinder {

    private static final String JBOSS_JAXWS_CLIENT_MODULE = "org.jboss.ws.jaxws-client";
    /**
     * Creates an instance of the specified class using the specified 
     * <code>ClassLoader</code> object.
     *
     * @exception WebServiceException if the given class could not be found
     *            or could not be instantiated
     */
    private static Object newInstance(String className,
                                      ClassLoader classLoader)
    {
        try {
            Class spiClass = safeLoadClass(className, classLoader);
            return spiClass.newInstance();
        } catch (ClassNotFoundException x) {
            throw new WebServiceException(
                "Provider " + className + " not found", x);
        } catch (Exception x) {
            throw new WebServiceException(
                "Provider " + className + " could not be instantiated: " + x,
                x);
        }
    }

    /**
     * Finds the implementation <code>Class</code> object for the given
     * factory name, or if that fails, finds the <code>Class</code> object
     * for the given fallback class name. The arguments supplied MUST be
     * used in order. If using the first argument is successful, the second
     * one will not be used.
     * <P>
     * This method is package private so that this code can be shared.
     *
     * @return the <code>Class</code> object of the specified message factory;
     *         may not be <code>null</code>
     *
     * @param factoryId             the name of the factory to find, which is
     *                              a system property
     * @param fallbackClassName     the implementation class name, which is
     *                              to be used only if nothing else
     *                              is found; <code>null</code> to indicate that
     *                              there is no fallback class name
     * @exception WebServiceException if there is an error
     */
    static Object find(String factoryId, String fallbackClassName)
    {
        ClassLoader classLoader;
        try {
            classLoader = Thread.currentThread().getContextClassLoader();
        } catch (Exception x) {
            throw new WebServiceException(x.toString(), x);
        }

        String serviceId = "META-INF/services/" + factoryId;
        // try to find services in CLASSPATH
        try {
            InputStream is=null;
            if (classLoader == null) {
                is=ClassLoader.getSystemResourceAsStream(serviceId);
            } else {
                is=classLoader.getResourceAsStream(serviceId);
            }
        
            if( is!=null ) {
                BufferedReader rd =
                    new BufferedReader(new InputStreamReader(is, "UTF-8"));
        
                String factoryClassName = rd.readLine();
                rd.close();

                if (factoryClassName != null &&
                    ! "".equals(factoryClassName)) {
                    return newInstance(factoryClassName, classLoader);
                }
            }
        } catch( Exception ex ) {
        }
        

        // try to read from $java.home/lib/jaxws.properties
        try {
            String javah=System.getProperty( "java.home" );
            String configFile = javah + File.separator +
                "lib" + File.separator + "jaxws.properties";
            File f=new File( configFile );
            if( f.exists()) {
                Properties props=new Properties();
                props.load( new FileInputStream(f));
                String factoryClassName = props.getProperty(factoryId);
                return newInstance(factoryClassName, classLoader);
            }
        } catch(Exception ex ) {
        }


        // Use the system property
        try {
            String systemProp =
                System.getProperty( factoryId );
            if( systemProp!=null) {
                return newInstance(systemProp, classLoader);
            }
        } catch (SecurityException se) {
        }

        ClassLoader moduleClassLoader = getModuleClassLoader();
        if (moduleClassLoader != null) {
           try {
              InputStream is = moduleClassLoader.getResourceAsStream(serviceId);
          
              if( is!=null ) {
                  BufferedReader rd =
                      new BufferedReader(new InputStreamReader(is, "UTF-8"));
          
                  String factoryClassName = rd.readLine();
                  rd.close();

                  if (factoryClassName != null &&
                      ! "".equals(factoryClassName)) {
                      return newInstance(factoryClassName, moduleClassLoader);
                  }
              }
          } catch( Exception ex ) {
          }
        }

        if (fallbackClassName == null) {
            throw new WebServiceException(
                "Provider for " + factoryId + " cannot be found", null);
        }

        return newInstance(fallbackClassName, classLoader);
    }

    private static ClassLoader getModuleClassLoader() throws WebServiceException {
        try {
            final Class<?> moduleClass = Class.forName("org.jboss.modules.Module");
            final Class<?> moduleIdentifierClass = Class.forName("org.jboss.modules.ModuleIdentifier");
            final Class<?> moduleLoaderClass = Class.forName("org.jboss.modules.ModuleLoader");
            final Object moduleLoader;
            final SecurityManager sm = System.getSecurityManager();
            if (sm == null) {
                moduleLoader = moduleClass.getMethod("getBootModuleLoader").invoke(null);
            } else {
                try {
                    moduleLoader = AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {
                        public Object run() throws Exception {
                            return moduleClass.getMethod("getBootModuleLoader").invoke(null);
                        }
                    });
                } catch (PrivilegedActionException pae) {
                    throw (WebServiceException) pae.getException();
                }
            }
            Object moduleIdentifier = moduleIdentifierClass.getMethod("create", String.class).invoke(null, JBOSS_JAXWS_CLIENT_MODULE);
            Object module = moduleLoaderClass.getMethod("loadModule", moduleIdentifierClass).invoke(moduleLoader, moduleIdentifier);
            return (ClassLoader)moduleClass.getMethod("getClassLoader").invoke(module);
         } catch (ClassNotFoundException e) {
            //ignore, JBoss Modules might not be available at all
             return null;
         } catch (Exception e) {
            throw new WebServiceException(e);
         }
    }

    /**
     * Loads the class, provided that the calling thread has an access to the class being loaded.
     */
    private static Class safeLoadClass(String className, ClassLoader classLoader) throws ClassNotFoundException {
        try {
            // make sure that the current thread has an access to the package of the given name.
            SecurityManager s = System.getSecurityManager();
            if (s != null) {
                int i = className.lastIndexOf('.');
                if (i != -1) {
                    s.checkPackageAccess(className.substring(0, i));
                }
            }

            if (classLoader == null)
                return Class.forName(className);
            else
                return classLoader.loadClass(className);
        } catch (SecurityException se) {
            // anyone can access the platform default factory class without permission
            if (Provider.DEFAULT_JAXWSPROVIDER.equals(className))
                return Class.forName(className);
            throw se;
        }
    }

}
