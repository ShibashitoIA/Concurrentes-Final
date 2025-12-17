package com.raft.client.protocol;

/**
 * Tipos de comandos que el cliente puede enviar al servidor RAFT.
 */
public enum CommandType {
    // Entrenamiento
    TRAIN,              // Entrenar un modelo (CSV datos numéricos)
    TRAIN_IMAGE,        // Entrenar con dataset de imágenes
    
    // Predicción
    PREDICT,            // Hacer predicción con datos numéricos
    PREDICT_IMAGE,      // Hacer predicción con imagen
    
    // Gestión de modelos
    LIST_MODELS,        // Listar modelos disponibles
    GET_MODEL,          // Obtener información de un modelo
    DELETE_MODEL,       // Eliminar un modelo
    REGISTER_MODEL,     // Registrar un modelo
    
    // Gestión de archivos
    STORE_FILE,         // Almacenar un archivo
    DOWNLOAD_FILE,      // Descargar un archivo
    LIST_FILES,         // Listar archivos disponibles
    DELETE_FILE,        // Eliminar un archivo
    UPLOAD_IMAGE,       // Subir imagen individual
    
    // Sistema
    STATUS              // Obtener estado del nodo
}
