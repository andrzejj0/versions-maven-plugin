package org.codehaus.mojo.versions.utils;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.apache.maven.model.InputLocation;
import org.apache.maven.model.Model;
import org.apache.maven.project.ProjectBuilderConfiguration;
import org.apache.maven.project.interpolation.ModelInterpolationException;
import org.apache.maven.project.interpolation.ModelInterpolator;
import org.apache.maven.project.interpolation.StringSearchModelInterpolator;
import org.apache.maven.project.path.PathTranslator;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.interpolation.InterpolationPostProcessor;
import org.codehaus.plexus.interpolation.ValueSource;
import org.codehaus.plexus.logging.Logger;

@Deprecated
@Component( role = ModelInterpolator.class, hint = "test" )
public class TestStringSearchModelInterpolator
        extends StringSearchModelInterpolator
{
    private static final Map<Class<?>, Field[]> fieldsByClass = new WeakHashMap<Class<?>, Field[]>();
    private static final Map<Class<?>, Boolean> fieldIsPrimitiveByClass = new WeakHashMap<Class<?>, Boolean>();

    public TestStringSearchModelInterpolator()
    {
    }

    public TestStringSearchModelInterpolator( PathTranslator pathTranslator )
    {
        super( pathTranslator );
    }

    protected void interpolateObject( Object obj, Model model, File projectDir, ProjectBuilderConfiguration config,
                                      boolean debugEnabled )
            throws ModelInterpolationException
    {
        try
        {
            List<ValueSource> valueSources = createValueSources( model, projectDir, config );
            List<InterpolationPostProcessor> postProcessors = createPostProcessors( model, projectDir, config );

            InterpolateObjectAction action =
                    new InterpolateObjectAction( obj, valueSources, postProcessors, debugEnabled,
                            this, getLogger() );

            ModelInterpolationException error = AccessController.doPrivileged( action );

            if ( error != null )
            {
                throw error;
            }
        }
        finally
        {
            getInterpolator().clearAnswers();
        }
    }

    private static final class InterpolateObjectAction implements PrivilegedAction<ModelInterpolationException>
    {

        private final boolean debugEnabled;
        private final LinkedList<Object> interpolationTargets;
        private final TestStringSearchModelInterpolator modelInterpolator;
        private final Logger logger;
        private final List<ValueSource> valueSources;
        private final List<InterpolationPostProcessor> postProcessors;
        private Object parent;

        private Set<Object> processedObjects = new HashSet<>();

        public InterpolateObjectAction( Object target, List<ValueSource> valueSources,
                                        List<InterpolationPostProcessor> postProcessors, boolean debugEnabled,
                                        TestStringSearchModelInterpolator modelInterpolator, Logger logger )
        {
            this.valueSources = valueSources;
            this.postProcessors = postProcessors;
            this.debugEnabled = debugEnabled;

            this.interpolationTargets = new LinkedList<Object>();
            interpolationTargets.add( target );

            this.modelInterpolator = modelInterpolator;
            this.logger = logger;
        }

        public ModelInterpolationException run()
        {
            while ( !interpolationTargets.isEmpty() )
            {
                Object obj = interpolationTargets.removeFirst();
                logger.debug( "Processing " + obj.getClass().getSimpleName() + ":" + obj
                        + ( parent != null ? " (parent: " + parent.getClass().getSimpleName() + ":" + parent + ")"
                        : "" ) );
                if ( processedObjects.contains( obj ) )
                {
                    logger.warn( "Already processed!" );
                }
                if ( obj instanceof InputLocation )
                {
                    logger.warn( "Input location" );
                }
                processedObjects.add( obj );
                try
                {
                    traverseObjectWithParents( obj.getClass(), obj );
                }
                catch ( ModelInterpolationException e )
                {
                    return e;
                }
            }

            return null;
        }

        @SuppressWarnings( "unchecked" )
        private void traverseObjectWithParents( Class<?> cls, Object target )
                throws ModelInterpolationException
        {
            if ( cls == null )
            {
                return;
            }


            if ( cls.isArray() )
            {
                evaluateArray( target );
            }
            else if ( isQualifiedForInterpolation( cls ) )
            {
                Field[] fields = fieldsByClass.get( cls );
                if ( fields == null )
                {
                    fields = cls.getDeclaredFields();
                    fieldsByClass.put( cls, fields );
                }

                for ( int i = 0; i < fields.length; i++ )
                {
                    Class<?> type = fields[i].getType();
                    if ( isQualifiedForInterpolation( fields[i], type ) )
                    {
                        boolean isAccessible = fields[i].isAccessible();
                        fields[i].setAccessible( true );
                        try
                        {
                            try
                            {
                                if ( String.class == type )
                                {
                                    String value = (String) fields[i].get( target );
                                    if ( value != null )
                                    {
                                        String interpolated = modelInterpolator.interpolateInternal( value, valueSources, postProcessors, debugEnabled );

                                        if ( !interpolated.equals( value ) )
                                        {
                                            fields[i].set( target, interpolated );
                                        }
                                    }
                                }
                                else if ( Collection.class.isAssignableFrom( type ) )
                                {
                                    Collection<Object> c = (Collection<Object>) fields[i].get( target );
                                    if ( c != null && !c.isEmpty() )
                                    {
                                        List<Object> originalValues = new ArrayList<Object>( c );
                                        try
                                        {
                                            c.clear();
                                        }
                                        catch ( UnsupportedOperationException e )
                                        {
                                            if ( debugEnabled && logger != null )
                                            {
                                                logger.debug( "Skipping interpolation of field: " + fields[i] + " in: "
                                                        + cls.getName() + "; it is an unmodifiable collection." );
                                            }
                                            continue;
                                        }

                                        for ( Object value : originalValues )
                                        {
                                            if ( value != null )
                                            {
                                                if ( String.class == value.getClass() )
                                                {
                                                    String interpolated =
                                                            modelInterpolator.interpolateInternal( (String) value,
                                                                    valueSources,
                                                                    postProcessors,
                                                                    debugEnabled );

                                                    if ( !interpolated.equals( value ) )
                                                    {
                                                        c.add( interpolated );
                                                    }
                                                    else
                                                    {
                                                        c.add( value );
                                                    }
                                                }
                                                else
                                                {
                                                    c.add( value );
                                                    if ( value.getClass().isArray() )
                                                    {
                                                        evaluateArray( value );
                                                    }
                                                    else
                                                    {
                                                        parent = target;
                                                        interpolationTargets.add( value );
                                                    }
                                                }
                                            }
                                            else
                                            {
                                                // add the null back in...not sure what else to do...
                                                c.add( value );
                                            }
                                        }
                                    }
                                }
                                else if ( Map.class.isAssignableFrom( type ) )
                                {
                                    Map<Object, Object> m = (Map<Object, Object>) fields[i].get( target );
                                    if ( m != null && !m.isEmpty() )
                                    {
                                        for ( Map.Entry<Object, Object> entry : m.entrySet() )
                                        {
                                            Object value = entry.getValue();

                                            if ( value != null )
                                            {
                                                if ( String.class == value.getClass() )
                                                {
                                                    String interpolated =
                                                            modelInterpolator.interpolateInternal( (String) value,
                                                                    valueSources,
                                                                    postProcessors,
                                                                    debugEnabled );

                                                    if ( !interpolated.equals( value ) )
                                                    {
                                                        try
                                                        {
                                                            entry.setValue( interpolated );
                                                        }
                                                        catch ( UnsupportedOperationException e )
                                                        {
                                                            if ( debugEnabled && logger != null )
                                                            {
                                                                logger.debug( "Skipping interpolation of field: "
                                                                        + fields[i] + " (key: " + entry.getKey() + ") in: "
                                                                        + cls.getName()
                                                                        + "; it is an unmodifiable collection." );
                                                            }
                                                            continue;
                                                        }
                                                    }
                                                }
                                                else
                                                {
                                                    if ( value.getClass().isArray() )
                                                    {
                                                        evaluateArray( value );
                                                    }
                                                    else
                                                    {
                                                        parent = target;
                                                        interpolationTargets.add( value );
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                else
                                {
                                    Object value = fields[i].get( target );
                                    if ( value != null )
                                    {
                                        if ( fields[i].getType().isArray() )
                                        {
                                            evaluateArray( value );
                                        }
                                        else
                                        {
                                            parent = target;
                                            interpolationTargets.add( value );
                                        }
                                    }
                                }
                            }
                            catch ( IllegalArgumentException e )
                            {
                                throw new ModelInterpolationException( "Failed to interpolate field: " + fields[i]
                                        + " on class: " + cls.getName(), e );
                            }
                            catch ( IllegalAccessException e )
                            {
                                throw new ModelInterpolationException( "Failed to interpolate field: " + fields[i]
                                        + " on class: " + cls.getName(), e );
                            }
                        }
                        finally
                        {
                            fields[i].setAccessible( isAccessible );
                        }
                    }
                }

                traverseObjectWithParents( cls.getSuperclass(), target );
            }
        }

        private boolean isQualifiedForInterpolation( Class<?> cls )
        {
            return !cls.getPackage().getName().startsWith( "java" );
        }

        private boolean isQualifiedForInterpolation( Field field, Class<?> fieldType )
        {
            if ( !fieldIsPrimitiveByClass.containsKey( fieldType ) )
            {
                fieldIsPrimitiveByClass.put( fieldType, Boolean.valueOf( fieldType.isPrimitive() ) );
            }

            if ( fieldIsPrimitiveByClass.get( fieldType ).booleanValue() )
            {
                return false;
            }

            //            if ( fieldType.isPrimitive() )
            //            {
            //                return false;
            //            }

            if ( "parent".equals( field.getName() ) )
            {
                return false;
            }

            return true;
        }

        private void evaluateArray( Object target )
                throws ModelInterpolationException
        {
            int len = Array.getLength( target );
            for ( int i = 0; i < len; i++ )
            {
                Object value = Array.get( target, i );
                if ( value != null )
                {
                    if ( String.class == value.getClass() )
                    {
                        String interpolated =
                                modelInterpolator.interpolateInternal( (String) value, valueSources, postProcessors,
                                        debugEnabled );

                        if ( !interpolated.equals( value ) )
                        {
                            Array.set( target, i, interpolated );
                        }
                    }
                    else
                    {
                        interpolationTargets.add( value );
                    }
                }
            }
        }
    }

}
