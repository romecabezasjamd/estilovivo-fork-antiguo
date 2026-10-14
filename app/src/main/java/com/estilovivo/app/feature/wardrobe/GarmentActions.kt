package com.estilovivo.app.feature.wardrobe

import com.estilovivo.app.core.data.ApiError
import com.estilovivo.app.core.data.ApiErrorKind
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.ImageUpload
import com.estilovivo.app.core.data.model.Garment
import com.estilovivo.app.core.data.model.toDraft
import com.estilovivo.app.core.data.repo.GarmentRepository

/**
 * Operaciones sobre una prenda existente, compartidas por la pestaña, el detalle y la lavadora.
 * Aquí viven las reglas (p. ej. una prenda en venta no va a la lavadora); la red la pone el repositorio.
 */
class GarmentActions(private val repo: GarmentRepository) {

    suspend fun setWashing(g: Garment, washing: Boolean): ApiResult<Garment> {
        if (washing && g.forSale) {
            return ApiResult.Failure(ApiError("Retira la prenda de la venta antes de meterla en la lavadora.", ApiErrorKind.Validation))
        }
        if (g.isWashing == washing) return ApiResult.Success(g)
        return repo.update(g.id, g.toDraft().copy(isWashing = washing))
    }

    suspend fun markWorn(id: String): ApiResult<Garment> = repo.markWorn(id)

    suspend fun delete(id: String): ApiResult<Unit> = repo.delete(id)

    /** Edición completa; [image] reemplaza la foto. */
    suspend fun edit(g: Garment, form: GarmentForm, image: ImageUpload?): ApiResult<Garment> {
        val errors = GarmentFormRules.validate(form, requirePhoto = false, requirePrice = form.forSale)
        if (errors.any) return invalid(errors)
        return repo.update(g.id, GarmentFormRules.draftForEdit(g, form), image)
    }

    /** Poner en venta una prenda del armario. */
    suspend fun sell(g: Garment, form: GarmentForm): ApiResult<Garment> {
        val errors = GarmentFormRules.validate(form.copy(name = g.name), requirePhoto = false, requirePrice = true)
        if (errors.any) return invalid(errors)
        return repo.update(g.id, GarmentFormRules.draftForSale(g, form))
    }

    /** Retirar de la venta (borra el precio). */
    suspend fun cancelSale(g: Garment): ApiResult<Garment> = repo.update(g.id, GarmentFormRules.draftCancelSale(g))

    private fun invalid(e: FormErrors) = ApiResult.Failure(
        ApiError(e.name ?: e.price ?: e.photo ?: "Revisa los datos.", ApiErrorKind.Validation),
    )
}
