package com.sigcon.backend.invoices.domain.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.sigcon.backend.assets.assets.application.ViewAssetsDTO;
import com.sigcon.backend.assets.assets.domain.model.Assets;
import com.sigcon.backend.assets.assets.domain.repository.AssetsRepository;
import com.sigcon.backend.invoices.application.InvoiceDTO;
import com.sigcon.backend.invoices.application.InvoiceFCRequestDTO;
import com.sigcon.backend.invoices.application.LineInvoiceDTO;
import com.sigcon.backend.invoices.application.LineInvoiceRequestDTO;
import com.sigcon.backend.invoices.application.LineInvoiceRulerTaxRequestDTO;
import com.sigcon.backend.invoices.domain.model.InvoiceStates;
import com.sigcon.backend.invoices.domain.model.Invoices;
import com.sigcon.backend.invoices.domain.model.LinesInvoice;
import com.sigcon.backend.invoices.domain.model.PaymentForms;
import com.sigcon.backend.invoices.domain.model.TypesInvoices;
import com.sigcon.backend.invoices.domain.model.enums.StatusesInvoices;
import com.sigcon.backend.invoices.domain.repository.InvoiceRepository;
import com.sigcon.backend.invoices.domain.repository.InvoiceStateRepository;
import com.sigcon.backend.invoices.domain.repository.LineInvoiceRepository;
import com.sigcon.backend.invoices.domain.repository.TypeInvoiceRepository;
import com.sigcon.backend.lists_accounting.ruler_tax.domain.model.TaxRulerEntity;
import com.sigcon.backend.lists_accounting.ruler_tax.domain.model.enums.TypeRulerTax;
import com.sigcon.backend.lists_accounting.ruler_tax.domain.repository.RuleTaxRepository;
import com.sigcon.backend.parametrization.resources.domain.repository.PaymentFormRepository;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.parametrization.users.domain.repository.UserRepository;
import com.sigcon.backend.parametrization.users.domain.service.UserService;
import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdParty;
import com.sigcon.backend.third_parties.third_parties.domain.repository.ThirdPartyRepository;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.UserUtil;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor

public class InvoiceService {
    private final InvoiceRepository invoiceRepository;
    private final LineInvoiceRepository linesInvoiceRepository;

    private final InvoiceStateRepository invoiceStateRepository;
    private final TypeInvoiceRepository typeInvoiceRepository;
    private final PaymentFormRepository paymentFormRepository;
    private final ThirdPartyRepository thirdPartyRepository;
    private final AssetsRepository assetRepository;
    private final RuleTaxRepository taxRulerRepository;

    private final DataTableSpecificationBuilder<Invoices> dataTableSpecificationBuilder = new DataTableSpecificationBuilder<>();

    private final UserUtil userUtil;


    @Transactional
    public Invoices createInvoice(InvoiceFCRequestDTO invoiceFCRequestDTO, Long typeInvoiceId) {

        Invoices invoice = new Invoices();
        TypesInvoices typeInvoice = typeInvoiceRepository.findById(typeInvoiceId).orElseThrow(
            () -> new RuntimeException("El tipo de factura no existe")
        );

        invoice.setTypeInvoice(typeInvoice);

        InvoiceStates invoiceState = getInvoiceState(typeInvoiceId, invoiceFCRequestDTO.getPaymentFormId());
        invoice.setInvoiceState(invoiceState);

        PaymentForms paymentForm = paymentFormRepository.findById(invoiceFCRequestDTO.getPaymentFormId())
            .orElseThrow(
                () -> new RuntimeException("La forma de pago no existe")
            );

        invoice.setPaymentForms(paymentForm);

        User user = userUtil.getUser();
        invoice.setUser(user);

        ThirdParty thirdParty = thirdPartyRepository.findById(invoiceFCRequestDTO.getThirdPartyId())
        .orElseThrow(
            () -> new RuntimeException("El tercero no existe")
        );
        invoice.setThirdParty(thirdParty);

        invoice.setCompany(user.getCompany());

        Invoices invoiceResolution = invoiceRepository.findByTypeInvoiceIdAndCompanyId(1l, user.getCompany().getId());

        String resolution = "1";
        if(invoiceResolution != null) {
            resolution = String.valueOf(Integer.parseInt(invoiceResolution.getResolution()) + 1);
        }

        invoice.setResolution(resolution);

        invoice.setResolutionInvoice(invoiceFCRequestDTO.getResolutionInvoice());

        invoice.setInvoiceDate(invoiceFCRequestDTO.getInvoiceDate());
        invoice.setInvoiceDueDay(invoiceFCRequestDTO.getInvoiceDueDay());

        if(invoiceFCRequestDTO.getPaymentFormId() == 1) {
            invoice.setStatus(StatusesInvoices.PENDING);
        }else{
            invoice.setStatus(StatusesInvoices.PAID);
        }

        invoice.setNotes(invoiceFCRequestDTO.getNotes());

        invoice = invoiceRepository.save(invoice);

        Double totalPayment = 0.0;
        Double totalAmount = 0.0;
        Double totalDiscount = 0.0;
        Double totalTax = 0.0;

        for(LineInvoiceRequestDTO lineInvoiceRequestDTO : invoiceFCRequestDTO.getLineInvoices()) {
            LinesInvoice lineInvoice = createLineInvoice(lineInvoiceRequestDTO, invoice);
            totalPayment += lineInvoice.getTotal();
            totalAmount += lineInvoice.getTotal();
            totalDiscount += lineInvoice.getDiscount();
            totalTax += lineInvoice.getTax();
        }
        invoice.setTotalPayment(totalPayment);
        invoice.setTotalAmount(totalAmount);
        invoice.setTotalDiscount(totalDiscount);
        invoice.setTotalTax(totalTax);
        invoice = invoiceRepository.save(invoice);
        return invoice;
    }

    @Transactional
    public LinesInvoice createLineInvoice(LineInvoiceRequestDTO lineInvoiceRequestDTO, Invoices invoice) {
        LinesInvoice lineInvoice = new LinesInvoice();
        lineInvoice.setInvoice(invoice);
        lineInvoice.setAsset(assetRepository.findById(lineInvoiceRequestDTO.getItemId()).orElseThrow(
            () -> new RuntimeException("El item no existe")
        ));
        lineInvoice.setQuantity(lineInvoiceRequestDTO.getQuantity());
        lineInvoice.setPrice(lineInvoiceRequestDTO.getPrice());

        Double total = (lineInvoiceRequestDTO.getPrice() * lineInvoiceRequestDTO.getQuantity());
        Double discount = 0.0;
        Double tax = 0.0;

        for(LineInvoiceRulerTaxRequestDTO taxRule : lineInvoiceRequestDTO.getTaxRulesIds()) {
            TaxRulerEntity taxRuleEntity = taxRulerRepository.findById(taxRule.getTaxId()).orElseThrow(
                () -> new RuntimeException("La regla tributaria no existe")
            );

            if(taxRuleEntity.getTypeRulerTax() == TypeRulerTax.TAX) {
                if(taxRule.getPercentage() != null) {
                    tax += (total * taxRule.getPercentage()) / 100;
                }else if(taxRule.getValue() != null) {
                    tax += taxRule.getValue();
                }
            }else if(taxRuleEntity.getTypeRulerTax() == TypeRulerTax.WITHHOLDING) {
                if(taxRule.getPercentage() != null) {
                    discount += (total * taxRule.getPercentage()) / 100;
                }else if(taxRule.getValue() != null) {
                    discount += taxRule.getValue();
                }
            }
        }

        lineInvoice.setDiscount(discount);
        lineInvoice.setTax(tax);


        lineInvoice.setTotal(
            total - discount + tax
        );

        return linesInvoiceRepository.save(lineInvoice);
    }


    public ResponseEntity<?> getInvoices(DataTableRequest request) {
        User user = userUtil.getUser();
        int start = Math.max(0, request.getStart());
        int length = request.getLength();
        int safeLength = length <= 0 ? 20 : Math.min(length, 100);
        int page = start / safeLength;

        Pageable pageable = length == -1
            ? Pageable.unpaged()
            : PageRequest.of(page, safeLength);

        Specification<Invoices> specification = dataTableSpecificationBuilder.build(request)
        .and((root, query, cb) -> cb.equal(root.get("company").get("id"), user.getCompany().getId()));

        Page<Invoices> invoicesPage = invoiceRepository.findAll(specification, pageable);
        return ResponseEntity.ok(DataTableResponse.from(invoicesPage.map(this::toDto), request.getDraw())); 
    }
    // ========================= Helpers

    private InvoiceStates getInvoiceState(Long typeInvoiceId, Long paymentFormId) {
        InvoiceStates invoiceState = null;
        switch(typeInvoiceId.intValue()) {
            case 1: // Factura de compra
                if(paymentFormId == 1) {
                    invoiceState = invoiceStateRepository.findById(1L).orElseThrow(
                        () -> new RuntimeException("El estado de la factura no existe")
                    );
                }else{
                    invoiceState = invoiceStateRepository.findById(2L).orElseThrow(
                        () -> new RuntimeException("El estado de la factura no existe")
                    );
                }
                break;
            default:
                throw new RuntimeException("El tipo de factura no existe");
        }

        return invoiceState;
    }

    private InvoiceDTO toDto(Invoices invoice) {

        List<LineInvoiceDTO> lineInvoices = new ArrayList<LineInvoiceDTO>();

        List<LinesInvoice> linesInvoice = linesInvoiceRepository.findAllByInvoiceId(invoice.getId());

        for(LinesInvoice lineInvoice : linesInvoice) {
            lineInvoices.add(toLineInvoiceDto(lineInvoice));
        }

        return InvoiceDTO.builder()
            .id(invoice.getId())
            .typeInvoice(invoice.getTypeInvoice())
            .invoiceState(invoice.getInvoiceState())
            .paymentForms(invoice.getPaymentForms())
            .user(invoice.getUser())
            .thirdParty(invoice.getThirdParty())
            .companyOrigin(invoice.getCompany())
            .companyLocationOrigin(invoice.getLocationOrigin())
            .companyLocationDestination(invoice.getLocationDestination())
            .invoiceReference(invoice.getInvoiceReference())
            .resolution(invoice.getResolution())
            .invoiceDate(invoice.getInvoiceDate())
            .invoiceDueDay(invoice.getInvoiceDueDay())
            .totalPayment(invoice.getTotalPayment())
            .totalAmount(invoice.getTotalAmount())
            .totalDiscount(invoice.getTotalDiscount())
            .totalTax(invoice.getTotalTax())
            .status(invoice.getStatus())
            .notes(invoice.getNotes())
            // .lineInvoices(invoice.getLineInvoices().stream().map(this::toLineDto).collect(Collectors.toList()))
            .build();
    }

    private LineInvoiceDTO toLineInvoiceDto(LinesInvoice lineInvoice) {
        return LineInvoiceDTO.builder()
            .id(lineInvoice.getId())
            .asset(toViewAssetsDto(lineInvoice.getAsset()))
            .quantity(lineInvoice.getQuantity())
            .unitPrice(lineInvoice.getPrice())
            .discount(lineInvoice.getDiscount())
            .tax(lineInvoice.getTax())
            .total(lineInvoice.getTotal())
            .build();
    }

    private ViewAssetsDTO toViewAssetsDto(Assets asset) {
        return ViewAssetsDTO.builder()
            .id(asset.getId())
            .assetCode(asset.getAssetCode())
            .name(asset.getAssetName())
            .description(asset.getDescription())
            .build();
    }

}
