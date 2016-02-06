/*
 * Copyright (c) 2015, Haiyang Li.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.landawn.abacus.util;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

import com.landawn.abacus.parser.ParserUtil;
import com.landawn.abacus.parser.ParserUtil.EntityInfo;
import com.landawn.abacus.parser.ParserUtil.PropInfo;
import com.landawn.abacus.type.Type;

/**
 * 
 * @since 0.8
 * 
 * @author Haiyang Li
 */
public final class JSONUtil {

    private JSONUtil() {
        // singlton.
    }

    public static JSONObject wrap(final Map<String, Object> map) {
        return new JSONObject(map);
    }

    public static JSONObject wrap(final Object entity) {
        return new JSONObject(entity instanceof Map ? (Map<String, Object>) entity : N.entity2Map(entity));
    }

    public static JSONArray wrap(final boolean[] array) {
        return new JSONArray(array);
    }

    public static JSONArray wrap(final char[] array) {
        return new JSONArray(array);
    }

    public static JSONArray wrap(final byte[] array) {
        return new JSONArray(array);
    }

    public static JSONArray wrap(final short[] array) {
        return new JSONArray(array);
    }

    public static JSONArray wrap(final int[] array) {
        return new JSONArray(array);
    }

    public static JSONArray wrap(final long[] array) {
        return new JSONArray(array);
    }

    public static JSONArray wrap(final float[] array) {
        return new JSONArray(array);
    }

    public static JSONArray wrap(final double[] array) {
        return new JSONArray(array);
    }

    public static JSONArray wrap(final Object[] array) {
        return new JSONArray(array);
    }

    public static JSONArray wrap(final Collection<?> coll) {
        return new JSONArray(coll);
    }

    public static Map<String, Object> unwrap(final JSONObject jsonObject) {
        return unwrap(Map.class, jsonObject);
    }

    public static <T> T unwrap(final Class<?> cls, final JSONObject jsonObject) {
        if (Map.class.isAssignableFrom(cls)) {
            final Map<String, Object> map = (Map<String, Object>) N.newInstance(cls);
            final Set<String> keySet = jsonObject.keySet();
            Object value = null;

            for (String key : keySet) {
                value = jsonObject.get(key);

                if (value == JSONObject.NULL) {
                    value = null;
                } else if (value != null) {
                    if (value instanceof JSONObject) {
                        value = unwrap((JSONObject) value);
                    } else if (value instanceof JSONArray) {
                        value = unwrap((JSONArray) value);
                    }
                }

                map.put(key, value);
            }

            return (T) map;
        } else {
            final Object entity = N.newEntity(cls, null);
            final EntityInfo entityInfo = ParserUtil.getEntityInfo(cls);
            final Set<String> keySet = jsonObject.keySet();
            PropInfo propInfo = null;
            Object value = null;

            for (String key : keySet) {
                propInfo = entityInfo.getPropInfo(key);
                value = jsonObject.get(key);

                if (value == JSONObject.NULL) {
                    value = null;
                } else if (value != null) {
                    if (value instanceof JSONObject) {
                        value = unwrap(propInfo.type.getTypeClass(), (JSONObject) value);
                    } else if (value instanceof JSONArray) {
                        value = unwrap(propInfo.type, (JSONArray) value);
                    }
                }

                propInfo.setPropValue(entity, value);
            }

            return (T) entity;
        }
    }

    public static <T> List<T> unwrap(final JSONArray jsonArray) {
        return unwrap(List.class, jsonArray);
    }

    /**
     * 
     * @param cls
     *            array or collection class
     * @param jsonArray
     * @return
     */
    public static <T> T unwrap(final Class<?> cls, final JSONArray jsonArray) {
        return unwrap(N.getType(cls), jsonArray);
    }

    public static <T> T unwrap(final Type<?> type, final JSONArray jsonArray) {
        final int len = jsonArray.length();

        if (type.isCollection()) {
            final Collection<Object> coll = (Collection<Object>) N.newInstance(type.getTypeClass());
            Object element = null;

            for (int i = 0; i < len; i++) {
                element = jsonArray.get(i);

                if (element == JSONObject.NULL) {
                    element = null;
                } else if (element != null) {
                    if (element instanceof JSONObject) {
                        element = type.getElementType().isEntity() ? unwrap(type.getElementType().getTypeClass(), (JSONObject) element)
                                : unwrap((JSONObject) element);
                    } else if (element instanceof JSONArray) {
                        element = (type.getElementType().isCollection() || type.getElementType().isArray())
                                ? unwrap(type.getElementType(), (JSONArray) element) : unwrap((JSONArray) element);
                    }
                }

                coll.add(element);
            }

            return (T) coll;
        } else if (type.isPrimitiveArray()) {
            final Object array = N.newArray(type.getElementType().getTypeClass(), jsonArray.length());
            Object element = null;

            for (int i = 0; i < len; i++) {
                element = jsonArray.get(i);

                if (element == JSONObject.NULL) {
                    element = null;
                }

                if (element == null) {
                    element = type.getElementType().defaultValue();
                }

                Array.set(array, i, element);
            }

            return (T) array;
        } else if (type.isArray()) {
            final Object[] array = N.newArray(type.getElementType().getTypeClass(), jsonArray.length());
            Object element = null;

            for (int i = 0; i < len; i++) {
                element = jsonArray.get(i);

                if (element == JSONObject.NULL) {
                    element = null;
                } else if (element != null) {
                    if (element instanceof JSONObject) {
                        element = type.getElementType().isEntity() ? unwrap(type.getElementType().getTypeClass(), (JSONObject) element)
                                : unwrap((JSONObject) element);
                    } else if (element instanceof JSONArray) {
                        element = (type.getElementType().isCollection() || type.getElementType().isArray())
                                ? unwrap(type.getElementType(), (JSONArray) element) : unwrap((JSONArray) element);
                    }
                }

                array[i] = element;
            }

            return (T) array;
        } else {
            throw new IllegalArgumentException(type.getName() + " is not a array or collection type");
        }
    }
}
