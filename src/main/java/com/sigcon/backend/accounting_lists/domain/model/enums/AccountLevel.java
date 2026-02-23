package com.sigcon.backend.accounting_lists.domain.model.enums;

public enum AccountLevel {

    //Los cuatro niveles base del PUC colombiano,
    //que se pueden extender en el futuro si se necesitan niveles más detallados
    //se coloca el level CLASS ya que aun así debe estar en el enum

    CLASS,       // Clase
    GROUP,       // Grupo
    ACCOUNT,     // Cuenta
    SUBACCOUNT   // Subcuenta
}
