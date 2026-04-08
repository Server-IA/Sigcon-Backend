package com.sigcon.backend.invoices.application;

import java.time.LocalDate;
import java.util.List;

import com.sigcon.backend.assets.assets.domain.model.Assets;
import com.sigcon.backend.invoices.domain.model.InvoiceStates;
import com.sigcon.backend.invoices.domain.model.Invoices;
import com.sigcon.backend.invoices.domain.model.PaymentForms;
import com.sigcon.backend.invoices.domain.model.TypesInvoices;
import com.sigcon.backend.invoices.domain.model.enums.StatusesInvoices;
import com.sigcon.backend.parametrization.companies.domain.model.Company;
import com.sigcon.backend.parametrization.companies.domain.model.CompanyLocation;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdParty;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor

@Tag(name = "Factura", description = "Datos de la factura FC")
public class InvoiceDTO {
    
    @Schema(description = "ID de la factura")
    private Long id;

    @Schema(description = "Tipo de factura")
    private TypesInvoices typeInvoice;

    @Schema(description = "Estado de la factura")
    private InvoiceStates invoiceState;
    
    @Schema(description = "Forma de pago")
    private PaymentForms paymentForms;
    
    @Schema(description = "Usuario que la creó")
    private User user;

    @Schema(description = "Tercero")
    private ThirdParty thirdParty;

    @Schema(description = "Empresa origen")
    private Company companyOrigin;

    @Schema(description = "Ubicacion de origen")
    private CompanyLocation companyLocationOrigin;

    @Schema(description = "Ubicacion de destino")
    private CompanyLocation companyLocationDestination;

    @Schema(description = "Factura de referencia")
    private Invoices invoiceReference;

    @Schema(description = "Resolución de la factura")
    private String resolution;

    @Schema(description = "Fecha de la factura")
    private LocalDate invoiceDate;

    @Schema(description = "Día de vencimiento de la factura")
    private Integer invoiceDueDay;

    @Schema(description = "Total de pago")
    private Double totalPayment;

    @Schema(description = "Total de la factura")
    private Double totalAmount;

    @Schema(description = "Total de descuento")
    private Double totalDiscount;

    @Schema(description = "Total de impuestos")
    private Double totalTax;

    @Schema(description = "Estado de la factura")
    private StatusesInvoices status;

    @Schema(description = "Notas")
    private String notes;

    @Schema(description = "Detalles de la factura")
    private List<LineInvoiceDTO> lineInvoices;

}
