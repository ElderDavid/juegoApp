# Reglas específicas de la app. Las bibliotecas AndroidX/Firebase traen sus propias reglas.

# WorkManager (lo trae el SDK de anuncios) crea su base de datos Room por reflexión
# (WorkDatabase_Impl). Sin esto, R8 quita el constructor y la app se cierra al abrir:
# «Failed to create an instance of androidx.work.impl.WorkDatabase».
-keep class * extends androidx.room.RoomDatabase { <init>(); }
