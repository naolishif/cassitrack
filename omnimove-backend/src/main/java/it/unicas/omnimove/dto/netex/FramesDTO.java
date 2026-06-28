package it.unicas.omnimove.dto.netex;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

/**
 * Wrapper NeTEx per i frame eterogenei dentro CompositeFrame.
 * Ordine canonico NeTEx: ResourceFrame → SiteFrame → ServiceFrame.
 */
@Data
public class FramesDTO {

    @JacksonXmlProperty(localName = "ResourceFrame")
    private ResourceFrameDTO resourceFrame;

    @JacksonXmlProperty(localName = "SiteFrame")
    private SiteFrameDTO siteFrame;

    @JacksonXmlProperty(localName = "ServiceFrame")
    private ServiceFrameDTO serviceFrame;
}
