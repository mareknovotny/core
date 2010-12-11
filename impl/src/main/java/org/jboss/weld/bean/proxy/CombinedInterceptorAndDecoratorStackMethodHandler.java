package org.jboss.weld.bean.proxy;

import static org.jboss.weld.bean.proxy.InterceptionDecorationContext.startInterceptorContext;
import static org.jboss.weld.bean.proxy.InterceptionDecorationContext.endInterceptorContext;
import static org.jboss.weld.bean.proxy.InterceptionDecorationContext.getDisabledInterceptionContexts;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

import javassist.util.proxy.MethodHandler;
import org.jboss.weld.util.reflection.SecureReflections;

/**
 * A method handler that wraps the invocation of interceptors and decorators.
 *
 * @author Marius Bogoevici
 */
public class CombinedInterceptorAndDecoratorStackMethodHandler implements MethodHandler, Serializable
{

   private MethodHandler interceptorMethodHandler;

   private Object outerDecorator;


   public void setInterceptorMethodHandler(MethodHandler interceptorMethodHandler)
   {
      this.interceptorMethodHandler = interceptorMethodHandler;
   }

   public void setOuterDecorator(Object outerDecorator)
   {
      this.outerDecorator = outerDecorator;
   }

   private Set<CombinedInterceptorAndDecoratorStackMethodHandler> getDisabledHandlers()
   {

      return getDisabledInterceptionContexts().peek();
   }

   public Object invoke(Object self, Method thisMethod, Method proceed, Object[] args) throws Throwable
   {
      boolean externalContext = false;

      try
      {
         if (getDisabledInterceptionContexts().empty())
         {
            externalContext = true;
            startInterceptorContext();
         }
         if (!getDisabledHandlers().contains(this))
         {
            try
            {

               getDisabledHandlers().add(this);
               if (interceptorMethodHandler != null)
               {
                  if (proceed != null)
                  {
                     return this.interceptorMethodHandler.invoke(outerDecorator != null ? outerDecorator : self, thisMethod, thisMethod, args);
                  }
                  else
                  {
                     return this.interceptorMethodHandler.invoke(self, thisMethod, proceed, args);
                  }
               }
               else
               {
                  if (outerDecorator != null)
                  {
                     SecureReflections.ensureAccessible(thisMethod);
                     return thisMethod.invoke(outerDecorator, args);
                  }
               }
            }
            finally
            {
               this.getDisabledHandlers().remove(this);
            }
         }
         SecureReflections.ensureAccessible(proceed);
         return proceed.invoke(self, args);
      }
      catch (InvocationTargetException e)
      {
         throw e.getCause();
      }
      finally
      {
         if (externalContext)
         {
            endInterceptorContext();
         }
      }
   }


}