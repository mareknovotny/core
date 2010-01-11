/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,  
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.weld.introspector.jlr;

import static org.jboss.weld.logging.messages.ReflectionMessage.ANNOTATION_MAP_NULL;
import static org.jboss.weld.logging.messages.ReflectionMessage.DECLARED_ANNOTATION_MAP_NULL;
import static org.jboss.weld.util.reflection.Reflections.EMPTY_ANNOTATIONS;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.inject.Qualifier;

import org.jboss.weld.exceptions.WeldException;
import org.jboss.weld.introspector.WeldAnnotated;
import org.jboss.weld.literal.DefaultLiteral;
import org.jboss.weld.metadata.TypeStore;
import org.jboss.weld.resources.ClassTransformer;
import org.jboss.weld.util.Proxies;
import org.jboss.weld.util.collections.Arrays2;
import org.jboss.weld.util.collections.HashSetSupplier;
import org.jboss.weld.util.reflection.HierarchyDiscovery;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;

/**
 * Represents functionality common for all annotated items, mainly different
 * mappings of the annotations and meta-annotations
 * 
 * AbstractAnnotatedItem is an immutable class and therefore threadsafe
 * 
 * @author Pete Muir
 * @author Nicklas Karlsson
 * 
 * @param <T>
 * @param <S>
 * 
 * @see org.jboss.weld.introspector.WeldAnnotated
 */
public abstract class AbstractWeldAnnotated<T, S> implements WeldAnnotated<T, S>
{

   // The set of default binding types
   private static final Set<Annotation> DEFAULT_QUALIFIERS = Collections.<Annotation>singleton(DefaultLiteral.INSTANCE);

   /**
    * Builds the annotation map (annotation type -> annotation)
    * 
    * @param annotations The array of annotations to map
    * @return The annotation map
    */
   protected static Map<Class<? extends Annotation>, Annotation> buildAnnotationMap(Annotation[] annotations)
   {
      Map<Class<? extends Annotation>, Annotation> annotationMap = new HashMap<Class<? extends Annotation>, Annotation>();
      for (Annotation annotation : annotations)
      {
         annotationMap.put(annotation.annotationType(), annotation);
      }
      return annotationMap;
   }
   
   /**
    * Builds the annotation map (annotation type -> annotation)
    * 
    * @param annotations The array of annotations to map
    * @return The annotation map
    */
   protected static Map<Class<? extends Annotation>, Annotation> buildAnnotationMap(Iterable<Annotation> annotations)
   {
      Map<Class<? extends Annotation>, Annotation> annotationMap = new HashMap<Class<? extends Annotation>, Annotation>();
      for (Annotation annotation : annotations)
      {
         annotationMap.put(annotation.annotationType(), annotation);
      }
      return annotationMap;
   }
   
   
   protected static void addMetaAnnotations(SetMultimap<Class<? extends Annotation>, Annotation> metaAnnotationMap, Annotation annotation, Annotation[] metaAnnotations, boolean declared)
   {
      for (Annotation metaAnnotation : metaAnnotations)
      {
         addMetaAnnotation(metaAnnotationMap, annotation, metaAnnotation.annotationType(), declared);
      }
   }
   
   protected static void addMetaAnnotations(SetMultimap<Class<? extends Annotation>, Annotation> metaAnnotationMap, Annotation annotation, Iterable<Annotation> metaAnnotations, boolean declared)
   {
      for (Annotation metaAnnotation : metaAnnotations)
      {
         addMetaAnnotation(metaAnnotationMap, annotation, metaAnnotation.annotationType(), declared);
      }
   }
   
   private static void addMetaAnnotation(SetMultimap<Class<? extends Annotation>, Annotation> metaAnnotationMap, Annotation annotation, Class<? extends Annotation> metaAnnotationType, boolean declared)
   {
      // Only map meta-annotations we are interested in
      if (declared ? MAPPED_DECLARED_METAANNOTATIONS.contains(metaAnnotationType) : MAPPED_METAANNOTATIONS.contains(metaAnnotationType))
      {
         metaAnnotationMap.put(metaAnnotationType, annotation);
      }
   }
   
   // The annotation map (annotation type -> annotation) of the item
   private final BiMap<Class<? extends Annotation>, Annotation> annotationMap;
   // The meta-annotation map (annotation type -> set of annotations containing
   // meta-annotation) of the item
   private final SetMultimap<Class<? extends Annotation>, Annotation> metaAnnotationMap;
   
   private final Class<T> rawType;
   private final Type[] actualTypeArguments; 
   private final Type type;
   private final Set<Type> typeClosure;
   private final boolean proxyable;

   /**
    * Constructor
    * 
    * Also builds the meta-annotation map. Throws a NullPointerException if
    * trying to register a null map
    * 
    * @param annotationMap A map of annotation to register
    * 
    */
   public AbstractWeldAnnotated(Map<Class<? extends Annotation>, Annotation> annotationMap, Map<Class<? extends Annotation>, Annotation> declaredAnnotationMap, ClassTransformer classTransformer, Class<T> rawType, Type type, Set<Type> typeClosure)
   {
      if (annotationMap == null)
      {
         throw new WeldException(ANNOTATION_MAP_NULL);
      }
      this.annotationMap = HashBiMap.create(annotationMap.size());
      this.metaAnnotationMap = Multimaps.newSetMultimap(new HashMap<Class<? extends Annotation>, Collection<Annotation>>(), HashSetSupplier.<Annotation>instance());
      for (Annotation annotation : annotationMap.values())
      {
         addMetaAnnotations(metaAnnotationMap, annotation, annotation.annotationType().getAnnotations(), false);
         addMetaAnnotations(metaAnnotationMap, annotation, classTransformer.getTypeStore().get(annotation.annotationType()), false);
         this.annotationMap.put(annotation.annotationType(), annotation);
      }
      
      if (declaredAnnotationMap == null)
      {
         throw new WeldException(DECLARED_ANNOTATION_MAP_NULL);
      }
      this.rawType = rawType;
      this.type = type;
      if (type instanceof ParameterizedType)
      {
         this.actualTypeArguments = ((ParameterizedType) type).getActualTypeArguments();
      }
      else
      {
         this.actualTypeArguments = new Type[0];
      }
      this.typeClosure = typeClosure;
      this.proxyable = Proxies.isTypesProxyable(typeClosure);
   }

   protected AbstractWeldAnnotated(Map<Class<? extends Annotation>, Annotation> annotationMap, Map<Class<? extends Annotation>, Annotation> declaredAnnotationMap, TypeStore typeStore)
   {
      if (annotationMap == null)
      {
         throw new WeldException(ANNOTATION_MAP_NULL);
      }
      this.annotationMap = HashBiMap.create(annotationMap.size());
      this.metaAnnotationMap = Multimaps.newSetMultimap(new HashMap<Class<? extends Annotation>, Collection<Annotation>>(), HashSetSupplier.<Annotation>instance());
      for (Annotation annotation : annotationMap.values())
      {
         addMetaAnnotations(metaAnnotationMap, annotation, annotation.annotationType().getAnnotations(), false);
         addMetaAnnotations(metaAnnotationMap, annotation, typeStore.get(annotation.annotationType()), false);
         this.annotationMap.put(annotation.annotationType(), annotation);
      }
      
      if (declaredAnnotationMap == null)
      {
         throw new WeldException(DECLARED_ANNOTATION_MAP_NULL);
      }
      this.rawType = null;
      this.type = null;
      this.actualTypeArguments = new Type[0];
      this.typeClosure = null;
      this.proxyable = false;
   }

   /**
    * Compares two AbstractAnnotatedItems
    * 
    * @param other The other item
    * @return True if equals, false otherwise
    */
   @Override
   public boolean equals(Object other)
   {
      if (other instanceof WeldAnnotated<?, ?>)
      {
         WeldAnnotated<?, ?> that = (WeldAnnotated<?, ?>) other;
         return this.getAnnotations().equals(that.getAnnotations()) && this.getJavaClass().equals(that.getJavaClass()) && this.getActualTypeArguments().length == that.getActualTypeArguments().length && Arrays.equals(this.getActualTypeArguments(), that.getActualTypeArguments());
      }
      return false;
   }

   /**
    * Gets the hash code of the actual type
    * 
    * @return The hash code
    */
   @Override
   public int hashCode()
   {
      return getJavaClass().hashCode();
   }

   /**
    * Indicates if the type is proxyable to a set of pre-defined rules
    * 
    * @return True if proxyable, false otherwise.
    * 
    * @see org.jboss.weld.introspector.WeldAnnotated#isProxyable()
    */
   public boolean isProxyable()
   {
      return proxyable;
   }

   public Class<T> getJavaClass()
   {
      return rawType;
   }

   public Type[] getActualTypeArguments()
   {
      return Arrays2.copyOf(actualTypeArguments, actualTypeArguments.length);
   }

   public Set<Type> getInterfaceClosure()
   {
      Set<Type> types = new HashSet<Type>();
      for (Type t : rawType.getGenericInterfaces())
      {
         types.addAll(new HierarchyDiscovery(t).getTypeClosure());
      }
      return types;
   }

   public abstract S getDelegate();

   public boolean isParameterizedType()
   {
      return rawType.getTypeParameters().length > 0;
   }

   public Type getBaseType()
   {
      return type;
   }

   public Set<Type> getTypeClosure()
   {
      return typeClosure;
   }
   
   public Set<Annotation> getAnnotations()
   {
      return Collections.unmodifiableSet(annotationMap.values());
   }

   public Set<Annotation> getMetaAnnotations(Class<? extends Annotation> metaAnnotationType)
   {
      return Collections.unmodifiableSet(metaAnnotationMap.get(metaAnnotationType));
   }

   @Deprecated
   public Set<Annotation> getQualifiers()
   {
      if (getMetaAnnotations(Qualifier.class).size() > 0)
      {
         return Collections.unmodifiableSet(getMetaAnnotations(Qualifier.class));
      }
      else
      {
         return Collections.unmodifiableSet(DEFAULT_QUALIFIERS);
      }
   }

   @Deprecated
   public Annotation[] getBindingsAsArray()
   {
      return getQualifiers().toArray(EMPTY_ANNOTATIONS);
   }

   
   public <A extends Annotation> A getAnnotation(Class<A> annotationType)
   {
      return annotationType.cast(annotationMap.get(annotationType));
   }

   public boolean isAnnotationPresent(Class<? extends Annotation> annotationType)
   {
      return annotationMap.containsKey(annotationType);
   }
   
   Map<Class<? extends Annotation>, Annotation> getAnnotationMap()
   {
      return annotationMap;
   }

}