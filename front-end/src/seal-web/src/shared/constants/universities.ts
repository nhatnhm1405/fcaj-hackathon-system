// Universities located in Ho Chi Minh City, offered as a dropdown for external
// students during registration / profile completion. The list is intentionally
// broad; anything not covered is handled by the "Other" option, which reveals a
// free-text field so no external student is ever blocked from registering.
export const HCMC_UNIVERSITIES: string[] = [
    "VNU-HCM University of Technology (Bách Khoa)",
    "VNU-HCM University of Science (Khoa học Tự nhiên)",
    "VNU-HCM University of Information Technology (UIT)",
    "VNU-HCM University of Economics and Law (UEL)",
    "VNU-HCM University of Social Sciences and Humanities (USSH)",
    "VNU-HCM International University (IU)",
    "University of Economics Ho Chi Minh City (UEH)",
    "Ho Chi Minh City University of Technology (HUTECH)",
    "Ho Chi Minh City University of Technology and Education (HCMUTE)",
    "Industrial University of Ho Chi Minh City (IUH)",
    "Ho Chi Minh City University of Industry and Trade (HUIT)",
    "Ton Duc Thang University (TDTU)",
    "Nguyen Tat Thanh University (NTTU)",
    "Van Lang University (VLU)",
    "Hoa Sen University (HSU)",
    "Ho Chi Minh City Open University (HCMCOU)",
    "Saigon University (SGU)",
    "Banking University of Ho Chi Minh City (BUH)",
    "University of Finance – Marketing (UFM)",
    "Ho Chi Minh City University of Foreign Languages – Information Technology (HUFLIT)",
    "RMIT University Vietnam (Saigon South)",
    "University of Medicine and Pharmacy at Ho Chi Minh City (UMP)",
    "University of Architecture Ho Chi Minh City (UAH)",
    "Ho Chi Minh City University of Transport (UTH)",
    "Ho Chi Minh City University of Law (HCMULAW)",
    "Nong Lam University Ho Chi Minh City (NLU)",
    "University of Sport Ho Chi Minh City (UPES)",
    "Gia Dinh University (GDU)",
    "Van Hien University (VHU)",
    "FPT University (Ho Chi Minh City campus)",
];

// Sentinel value used by the dropdown to switch to the free-text "other" field.
export const UNIVERSITY_OTHER = "__OTHER__";
