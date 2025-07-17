ifeq ($(findstring true,$(TARGET_FWK_SUPPORTS_FULL_VALUEADDS) $(TARGET_BOARD_AUTO)),true)
ifneq ($(TARGET_HAS_LOW_RAM),true)
PRODUCT_PACKAGES += QtiWifiService
PRODUCT_PACKAGES += qti_supplicant_interface.xml
PRODUCT_PACKAGES += privapp-permissions-com.qualcomm.qti.server.qtiwifi.xml
ifneq (,$(filter userdebug eng, $(TARGET_BUILD_VARIANT)))
PRODUCT_PACKAGES += QtiWifiSettingsApp
endif # userdebug | eng
ifeq ($(TARGET_CONTROL_REMOTE_CEM_WLAN),true)
PRODUCT_PACKAGES += QtiWifiExtendService
PRODUCT_PACKAGES += statemachine-to-app
PRODUCT_PACKAGES += qtiwifi_extend_manager
PRODUCT_PACKAGES += android.hardware.wifi-V1-java
PRODUCT_PACKAGES += android.hardware.wifi.hostapd-V1-java
PRODUCT_PACKAGES += vendor.qti.hardware.wifi.hostapd-V1-java
PRODUCT_PACKAGES += vendor.qti.hardware.wifi.qtiwifi-V1-java
PRODUCT_PACKAGES += qti_hostapd_interface.xml
endif #ENABLE_SOMEIP
endif #TARGET_HAS_LOW_RAM
endif #TARGET_FWK_SUPPORTS_FULL_VALUEADDS | TARGET_BOARD_AUTO
