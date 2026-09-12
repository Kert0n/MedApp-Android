package com.kert0n.medapp.network.server

import kotlin.uuid.Uuid

/**
 * Пути ресурсов сервера MedApp (PLAN B4) — одно место для операций клиента и для замороженных
 * запросов очереди: путь, названный дважды, разошёлся бы молча.
 */
object MedAppRoutes {
    const val REGISTER = "/v1/auth/register"
    const val TOKEN = "/v1/auth/token"
    const val ME = "/v1/users/me"
    const val MED_KITS = "/v1/med-kits"
    const val MEMBERSHIPS = "/v1/med-kit-memberships"
    const val CLAIMS = "/v1/reservations"
    const val QUANTITY_UNITS = "/v1/quantity-units"
    const val FORM_TYPES = "/v1/form-types"
    const val TEMPLATES = "/v1/drug-templates"

    fun medKit(medKitId: Uuid) = "$MED_KITS/$medKitId"
    fun invitations(medKitId: Uuid) = "${medKit(medKitId)}/invitations"
    fun membership(medKitId: Uuid) = "$MEMBERSHIPS/$medKitId"
    fun packagesOf(medKitId: Uuid) = "${medKit(medKitId)}/drugs"
    fun packageIn(medKitId: Uuid, packageId: Uuid) = "${packagesOf(medKitId)}/$packageId"
    fun pack(packageId: Uuid) = "/v1/drugs/$packageId"
    fun sync(packageId: Uuid, syncId: Uuid) = "${pack(packageId)}/sync/$syncId"
    fun claim(packageId: Uuid) = "$CLAIMS/$packageId"
    fun template(templateId: Uuid) = "$TEMPLATES/$templateId"
}
