@echo off

java -classpath ../lib/crypto/core/*;../lib/crypto/3rdparties/* com.experian.eda.enterprise.cryptomanager.CryptoSys encryptData "C:\SIS_2_13/sis/key/AEScryptoKey.key" %1
pause